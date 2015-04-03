package controllers

import java.util.UUID
import java.util.concurrent.TimeoutException

import actors.{GetJobFuture, LongJob}
import akka.actor.{ActorRef, Props}
import akka.util.Timeout
import akka.pattern.ask
import play.api.libs.concurrent.Akka
import play.api.mvc.{Result, Action, Controller}
import play.api.Play.current
import scala.concurrent.{Promise, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

object Application extends Controller {

  def longJob(maybeId: Option[String]) = Action.async { request =>

    val (actorRefFuture, id) = maybeId.fold {
      // no existing job so create one
      val id = UUID.randomUUID().toString
      (Future.successful(Akka.system.actorOf(Props(new LongJob(id)), id)), id)
    } { id =>
      // existing job so get it's future
      (Akka.system.actorSelection(s"user/$id").resolveOne(1.second), id)
    }

    val requestFuture = for {
      actorRef  <- actorRefFuture
      jobFuture <- actorRef.ask(GetJobFuture)(Timeout(1.seconds)).mapTo[Future[String]]
      jobResult <- TimeoutFuture(25.seconds)(jobFuture) // 25 second time limit on getting a result
    } yield {
      // job completed
      Akka.system.stop(actorRef)
      Ok(jobResult)
    }

    requestFuture.recoverWith {
      case e: TimeoutException =>
        // job not completed
        Future.successful(Redirect(routes.Application.longJob(Some(id))))
      case e: Exception =>
        // job failed
        actorRefFuture.map { actorRef =>
          Akka.system.stop(actorRef)
          InternalServerError(e.getMessage)
        }
    }
  }

  // From: http://stackoverflow.com/questions/16304471/scala-futures-built-in-timeout
  object TimeoutFuture {
    def apply[A](timeout: FiniteDuration)(future: Future[A]): Future[A] = {

      val promise = Promise[A]()

      Akka.system.scheduler.scheduleOnce(timeout) {
        promise.tryFailure(new TimeoutException)
      }

      promise.completeWith(future)

      promise.future
    }
  }

}
