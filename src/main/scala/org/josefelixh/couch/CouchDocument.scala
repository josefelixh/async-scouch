package org.josefelixh.couch

import play.api.libs.functional._
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}
import org.josefelixh.libs.http.Response

object CouchDocument {
  implicit def toCouchDocument[T](t: T): CouchDocument[T] = CouchDocument(None, None, t)
}
case class CouchDocument[T](id: Option[String] = None, rev: Option[String] = None, doc: T) {

  def create(implicit couch: Couch, format: Format[T], execCtx: ExecutionContext): Future[CouchDocument[T]] = {
    def merge_Id(id: String) = (__).json.update {
      __.read[JsObject] map { o => Json.obj("_id" -> id) ++ o }
    }
    val jsDoc = id match {
      case Some(x) => Json.toJson(this.doc).transform(merge_Id(x)).asOpt
      case None => Some(Json.toJson(this.doc))
    }
    couch.db("/").post(jsDoc.get.toString()) map { IdAndRevision }
  }

  def retrieve(implicit couch: Couch, format: Format[T], execCtx: ExecutionContext): Future[CouchDocument[T]] =
    couch.db(s"/${id.get}").get() map { revisionAndDocument }

  def delete(implicit couch: Couch) =
    couch.db(s"/${id.get}").withQueryString(
      "rev" -> rev.get
    ).delete()

  def update(updator: T => T)(implicit couch: Couch, fmt: Format[T], execCtx: ExecutionContext): Future[CouchDocument[T]] =
    couch.db(s"/${id.get}").withQueryString(
      "rev" -> rev.get
    ).withHeaders(
      "If-Match" -> rev.get
    ).put(Json.toJson(updator(this.doc)).toString()) map {
      revisionAndDocument
    }

  private def revisionAndDocument(response: Response)(implicit fmt: Format[T]): CouchDocument[T] = {
    response.json.transform((__ \ '_id).json.prune andThen ((__ \ '_rev).json.prune)) map { js =>
      this.copy(
        rev = (response.json \ "_rev").validate[String].asOpt,
        doc = js.validate[T](implicitly[Reads[T]]).get
      )
    } getOrElse(throw new RuntimeException)
  }

  private val IdAndRevision: Response => CouchDocument[T] = { response =>
    (response.json \ "ok").validate[Boolean] map { isOk =>
      if (!isOk) throw new RuntimeException
    }

    this.copy(
      id = (response.json \ "id").validate[String].asOpt,
      rev = (response.json \ "rev").validate[String].asOpt
    )
  }
}