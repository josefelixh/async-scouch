package org.josefelixh

import org.josefelixh.couch._
import org.josefelixh.couch.CouchDocument._
import play.api.libs.json._
import scala.concurrent.{Future, Await}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global


case class Role(val name: String, val permissions: Int)
case class Profile(val id: String, level: Int, roles: Seq[Role])

object Asyncscouch extends App {
  import scala.language.postfixOps

  println("Hello, async-scouch")


  val role = Role("admin", 777)
  val profile = Profile("badger", 0, Vector(role))

  implicit val couch = Couch("heroku")
  implicit val roleFormat = Json.format[Role]
  implicit val profileFormat = Json.format[Profile]


  val future = for {
//    r1 <- ((profile) create)
    r2 <- (("Update_TEST", profile) create)
//    u1 <- r1 update (current => current.copy(level = 1))
    u2 <- r2 update (current => current.copy(level = 10))
    d2 <- u2 delete
  } yield {
//    println(s"Response1 : $r1")
    println(s"Response2 : $r2")
//    println(s"Update1 : $u1")
    println(s"Update2 : $u2")
    println(s"Delete2 : ${d2.json}")
  }

  Await.ready(future, 30 seconds)

  def deleteAll =
    couch.documents map { response =>
      println(s"Documents ${response.json}")
      val ids = (response.json \\ "id") map { _.validate[String].asOpt }
      for (id <- ids) yield id match {
        case x @ Some(_) => {
          println("Trying to delete id: " + id.get)
          for {
            doc <- CouchDocument[JsValue](x, None, JsNull).retrieve
            deleteResponse <- doc.delete
          } yield concurrent.future(println(s"Status: ${deleteResponse.statusText} Body: ${deleteResponse.body}"))
        }

        case None => concurrent.future(println("No Id found"))
    }}

//  Await.result(deleteAll, 30 seconds) map {f => Await.ready(f, 20 seconds)}

//  val fProfile = profile create
//
//  (for (id <- 1 to 1) yield {
//    ("PROFILE_"+id, None, profile) create
//  }) map( x => Await.ready(x, 10 seconds))



//  Thread.sleep(30000)
  System.exit(0)
}

