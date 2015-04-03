package actors

import akka.actor.Actor
import play.api.libs.concurrent.Promise

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

class LongJob(id: String) extends Actor {

  lazy val jobFuture: Future[String] = Promise.timeout("done!", 60.seconds)

  override def receive = {
    case GetJobFuture => sender ! jobFuture
  }

}

case object GetJobFuture