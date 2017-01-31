package com.nykolaslima.akkaapitemplate

import akka.actor.ActorSystem
import akka.http.scaladsl.server.RouteConcatenation._
import com.nykolaslima.akkaapitemplate.components.healthCheck.HealthCheckRoute
import com.nykolaslima.akkaapitemplate.components.swagger.SwaggerRoute
import com.nykolaslima.akkaapitemplate.components.user.UserRoute
import org.slf4j.LoggerFactory

class MainRoute()(implicit system: ActorSystem) {
  val log = LoggerFactory.getLogger(this.getClass)

  val healthCheck = new HealthCheckRoute {
    override implicit def actorSystem: ActorSystem = system
  }
  val swagger = new SwaggerRoute

  val user = new UserRoute {
    override implicit def actorSystem: ActorSystem = system
  }

  val routes = swagger.routes ~ healthCheck.routes ~ user.routes
}