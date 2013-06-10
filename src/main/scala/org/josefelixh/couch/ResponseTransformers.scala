package org.josefelixh.couch

import org.josefelixh.libs.http.Response
import play.api.libs.json.{Format, JsResult, Reads}

trait ResponseTransformers[T] {
  this: CouchDocument[T] =>
  private implicit def jsResultAsOpt[A](jsResult: JsResult[A]): Option[A] = jsResult.asOpt

  private[couch] def RevisionAndDocument(implicit response: Response, fmt: Format[T]): CouchDocument[T] = {
    def _rev = (response.json \ "_rev").validate[String]
    val transformer = PruneOk andThen { Prune_id andThen { Prune_rev } }

    transformResponse(transformer).map { json =>
      CouchDocument[T](
        id = id,
        rev = _rev,
        doc = json.validate[T](implicitly[Reads[T]]))
    } getOrElse(throw new RuntimeException)

  }

  private[couch] def Revision(updated: T)(implicit response: Response, fmt: Format[T]): CouchDocument[T] = {
    CouchDocument[T](
      id = id,
      rev = (response.json \ "rev").validate[String],
      doc = Some(updated)
    )
  }

  private[couch] def IdAndRevision(response: Response)(implicit fmt: Format[T]): CouchDocument[T] = {
    (response.json \ "ok").validate[Boolean] map { isOk =>
      if (!isOk) throw new RuntimeException
    }

    CouchDocument[T](
      id = (response.json \ "id").validate[String],
      rev = (response.json \ "rev").validate[String],
      doc = doc
    )(implicitly)
  }

}
