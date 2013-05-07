package org.josefelixh

import org.josefelixh.couch._
import org.josefelixh.couch.CouchDocument._
import play.api.libs.json._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


case class Role(val name: String, val permissions: Int)
case class Profile(val id: String, level: Int, roles: Seq[Role])

object Asyncscouch extends App {
  println("Hello, async-scouch")

  implicit val couch = Couch("heroku")

  val role = Role("admin", 777)
  val profile = Profile("badger", 0, Vector(role))

  implicit val roleFormat = Json.format[Role]
  implicit val profileFormat = Json.format[Profile]

  val fProfile = profile create

  (for (id <- 1 to 10) yield {
    CouchDocument(Some("0APROFILE_"+id), None, profile) create
  }) map( x => Await.ready(x, 30 seconds))



//  Thread.sleep(30000)
  System.exit(0)
}

