package io.hydrosphere.serving.manager.domain.model_version

import java.time.LocalDateTime

import cats.data.EitherT
import cats.implicits._
import com.spotify.docker.client.ProgressHandler
import com.spotify.docker.client.messages.ProgressMessage
import io.hydrosphere.serving.manager.domain.application.ApplicationRepositoryAlgebra
import io.hydrosphere.serving.manager.domain.build_script.BuildScriptServiceAlg
import io.hydrosphere.serving.manager.domain.host_selector.HostSelectorServiceAlg
import io.hydrosphere.serving.manager.domain.model.{Model, ModelVersionMetadata}
import io.hydrosphere.serving.model.api.Result.HError
import io.hydrosphere.serving.model.api.{HFResult, Result}
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.{ExecutionContext, Future}


trait ModelVersionServiceAlg {
  def deleteVersions(mvs: Seq[ModelVersion]): HFResult[Seq[ModelVersion]]

  def getNextModelVersion(modelId: Long): HFResult[Long]

  def list: Future[Seq[ModelVersionView]]

  def modelVersionsByModelVersionIds(modelIds: Set[Long]): Future[Seq[ModelVersion]]

  def listForModel(modelId: Long): HFResult[Seq[ModelVersion]]

  def delete(versionId: Long): HFResult[ModelVersion]

  def build(model: Model, metadata: ModelVersionMetadata): HFResult[BuildResult]
}

class ModelVersionService(
  modelVersionRepository: ModelVersionRepositoryAlgebra[Future],
  buildScriptService: BuildScriptServiceAlg,
  hostSelectorService: HostSelectorServiceAlg,
  modelBuildService: ModelBuildAlgebra,
  modelPushService: ModelVersionPushAlgebra,
  applicationRepo: ApplicationRepositoryAlgebra[Future]
)(implicit executionContext: ExecutionContext) extends ModelVersionServiceAlg with Logging {

  def deleteVersions(mvs: Seq[ModelVersion]): HFResult[Seq[ModelVersion]] = {
    Result.traverseF(mvs) { version =>
      delete(version.id)
    }
  }

  def getNextModelVersion(modelId: Long): HFResult[Long] = {
    modelVersionRepository.lastModelVersionByModel(modelId, 1).map { se =>
      val result = se.headOption.map(_.modelVersion + 1).getOrElse(1L)
      Right(result)
    }
  }

  def list: Future[Seq[ModelVersionView]] = {
    for {
      allVersions <- modelVersionRepository.all()
      f <- Future.traverse(allVersions.map(_.id)) { x =>
        applicationRepo.findVersionsUsage(x).map(x -> _)
      }
      usageMap = f.toMap
    } yield {
      allVersions.map { v =>
        ModelVersionView.fromVersion(v, usageMap.getOrElse(v.id, Seq.empty))
      }
    }
  }

  def modelVersionsByModelVersionIds(modelIds: Set[Long]): Future[Seq[ModelVersion]] = {
    modelVersionRepository.modelVersionsByModelVersionIds(modelIds)
  }

  def listForModel(modelId: Long): HFResult[Seq[ModelVersion]] = {
    modelVersionRepository.listForModel(modelId).map(Result.ok)
  }

  def delete(versionId: Long): HFResult[ModelVersion] = {
    val f = for {
      version <- EitherT.fromOptionF(modelVersionRepository.get(versionId), Result.ClientError(s"Can't find the version with id $versionId"))
      _ <- EitherT.liftF[Future, HError, Int](modelVersionRepository.delete(versionId))
    } yield version
    f.value
  }

  def build(model: Model, metadata: ModelVersionMetadata): HFResult[BuildResult] = {
    logger.debug(model)
    val messageHandler = new ProgressHandler {
      override def progress(message: ProgressMessage): Unit = {
        val msg = Option(message.stream()).getOrElse("")
        logger.info(msg)
      }
    }
    val f = for {
      version <- EitherT(getNextModelVersion(model.id))
      script <- EitherT.liftF(buildScriptService.fetchScriptForModelType(metadata.modelType))
      image = modelPushService.getImage(metadata.modelName, version)
      mv = ModelVersion(
        id = 0,
        image = image,
        created = LocalDateTime.now(),
        finished = None,
        modelVersion = version,
        modelType = metadata.modelType,
        modelContract = metadata.contract,
        runtime = metadata.runtime,
        model = model,
        hostSelector = metadata.hostSelector,
        status = ModelVersionStatus.Started,
        profileTypes = metadata.profileTypes
      )
      modelVersion <- EitherT.liftF[Future, HError, ModelVersion](modelVersionRepository.create(mv))
    } yield (script, modelVersion)

    val preBuilt = f.value

    val completedBuild = preBuilt.flatMap {
      case Left(err) =>
        logger.error(err)
        Future.failed(new IllegalArgumentException(err.toString))
      case Right((script, mv)) =>
        val res = EitherT(modelBuildService.build(BuildRequest.fromVersion(mv, script), messageHandler))
          .map { sha =>
            val newDockerImage = mv.image.copy(sha256 = Some(sha))
            mv.copy(image = newDockerImage, finished = Some(LocalDateTime.now()))
          }.value

        val f = res.flatMap {
          case Left(err) =>
            logger.error(err)
            val failed = mv.copy(status = ModelVersionStatus.Failed)
            modelVersionRepository.update(failed.id, failed)
              .map(_ => failed)
          case Right(smv) =>
            val finished = smv.copy(status = ModelVersionStatus.Finished)
            modelVersionRepository.update(finished.id, finished)
              .map { _ =>
                modelPushService.push(finished, messageHandler)
                finished
              }
        }

        f.failed.foreach { err =>
          logger.error(err)
          val failed = mv.copy(status = ModelVersionStatus.Failed)
          modelVersionRepository.update(failed.id, failed)
        }
        f
    }

    f.map(t => BuildResult(t._2, completedBuild)).value
  }
}