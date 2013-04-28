package org.josefelixh

import org.josefelixh.couch.CouchDB
import scala.util.parsing.json.JSON


object Asyncscouch extends App {
  println("Hello, async-scouch")

  import scala.concurrent.ExecutionContext.Implicits.global

  CouchDB("heroku").databases map {
    value => println(value.body)
  }

  val future = for {
    value <- CouchDB("heroku").create
  } yield JSON.parseFull(value.body)

  future map {
    case Some(x: Map[_, _]) => println(x.mkString("\n"))
    case _ => println("ooooops")
  }

  Thread.sleep(5000)
  System.exit(0)
}
