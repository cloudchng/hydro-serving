package io.hydrosphere.serving.manager.infrastructure.envoy.internal_events

import akka.actor.ActorSystem
import io.hydrosphere.serving.manager.domain.application.Application
import io.hydrosphere.serving.manager.domain.service.Service
import io.hydrosphere.serving.manager.domain.clouddriver.CloudService
import io.hydrosphere.serving.manager.service.internal_events._
import org.apache.logging.log4j.scala.Logging

trait ManagerEventBus {
  def applicationChanged(application: Application): Unit

  def applicationRemoved(application: Application): Unit

  def serviceChanged(service: Service): Unit

  def serviceRemoved(service: Service): Unit

  def cloudServiceDetected(cloudService: Seq[CloudService])

  def cloudServiceRemoved(cloudService: Seq[CloudService]): Unit
}

object ManagerEventBus {
  def fromActorSystem(actorSystem: ActorSystem): ManagerEventBus = new ManagerEventBus with Logging {
    def applicationChanged(application: Application): Unit = {
      logger.info(s"Application changed: $application")
      actorSystem.eventStream.publish(ApplicationChanged(application))
    }

    def applicationRemoved(application: Application): Unit = {
      logger.info(s"Application removed: $application")
      actorSystem.eventStream.publish(ApplicationRemoved(application))
    }

    def serviceChanged(service: Service): Unit = {
      logger.info(s"Service changed: $service")
      actorSystem.eventStream.publish(ServiceChanged(service))
    }

    def serviceRemoved(service: Service): Unit = {
      logger.info(s"Service removed: $service")
      actorSystem.eventStream.publish(ServiceRemoved(service))
    }

    def cloudServiceDetected(cloudService: Seq[CloudService]): Unit = {
      logger.info(s"Cloud service detected: $cloudService")
      actorSystem.eventStream.publish(CloudServiceDetected(cloudService))
    }

    def cloudServiceRemoved(cloudService: Seq[CloudService]): Unit = {
      logger.info(s"Cloud service removed: $cloudService")
      actorSystem.eventStream.publish(CloudServiceRemoved(cloudService))
    }
  }
}