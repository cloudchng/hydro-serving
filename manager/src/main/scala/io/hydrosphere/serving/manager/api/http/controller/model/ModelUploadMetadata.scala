package io.hydrosphere.serving.manager.api.http.controller.model

import io.hydrosphere.serving.contract.model_contract.ModelContract
import io.hydrosphere.serving.model.api.ModelType
import io.hydrosphere.serving.manager.data_profile_types.DataProfileType
import io.hydrosphere.serving.manager.domain.image.DockerImage

case class ModelUploadMetadata(
  name: Option[String] = None,
  modelType: Option[ModelType] = None,
  runtime: DockerImage,
  hostSelectorName: Option[String] = None,
  contract: Option[ModelContract] = None,
  profileTypes: Option[Map[String, DataProfileType]] = None
)