package io.hydrosphere.serving.manager.repository

import io.hydrosphere.serving.manager.model.db.ModelVersion

import scala.concurrent.Future

trait ModelVersionRepository extends BaseRepository[ModelVersion, Long] {
  def listForModel(modelId: Long): Future[Seq[ModelVersion]]

  def lastModelVersionByModel(modelId: Long, max: Int): Future[Seq[ModelVersion]]

  def modelVersionByModelAndVersion(modelId: Long, version: Long): Future[Option[ModelVersion]]

  def lastModelVersionForModels(modelIds: Seq[Long]): Future[Seq[ModelVersion]]

  def modelVersionsByModelVersionIds(modelVersionIds: Set[Long]): Future[Seq[ModelVersion]]
}
