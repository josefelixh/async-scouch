package org.josefelixh

import org.josefelixh.couch.{CouchDocument, Couch}
import scala.util.parsing.json.JSON


object Asyncscouch extends App {
  println("Hello, async-scouch")

  import scala.concurrent.ExecutionContext.Implicits.global

  implicit val couch = Couch("heroku")

  couch.databases map {
    value => println(value.body)
  }

  val future = for {
    value <- couch.documents
  } yield JSON.parseFull(value.body)

  future map {
    case Some(x: Map[_, _]) => println(x.mkString("\n"))
    case _ => println("ooooops")
  }

  val doc = "{\"system\":\"badger\",\"type\":\"mushroom\",\"value\":\"snake\"}"

//  CouchDocument(None, None, doc).create map { response =>
//    println(response.status + " " + response.body)
//  }
//
//  CouchDocument(Some("22790e05baaa21af392c8239807ea339"), None, "").retrieve map { response =>
//    println(response.status + " " + response.body)
//  }

  CouchDocument(Some("myid"), None, doc).create map { response =>
    println("CREATE: " + response.status + " " + response.body)

    CouchDocument(Some("myid"), None, doc).retrieve map { response =>
      println("RETRIEVE: " + response.status + " " + response.body)

      val rev = (JSON.parseFull(response.body).get.asInstanceOf[Map[String, Any]].get("_rev")).asInstanceOf[Option[String]]

      CouchDocument(Some("myid"), rev, doc).update map { response =>
        println("UPDATE: " + response.status + " " + response.body)

        val rev = (JSON.parseFull(response.body).get.asInstanceOf[Map[String, Any]].get("rev")).asInstanceOf[Option[String]]

        CouchDocument(Some("myid"), rev, doc).delete map { response =>
          println("DELETE: " + response.status + " " + response.body)
        }

      }
    }
  }



  Thread.sleep(10000)
  System.exit(0)
}
