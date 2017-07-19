package controllers

import models.{FeedbackForm, Question}

import scala.annotation.tailrec

object FeedbackFormsHelper {

  def jsonCountBuilder(feedForm: FeedbackForm): String = {
    @tailrec
    def builder(questions: List[Question], json: List[String], count: Int): List[String] = {
      questions match {
        case Nil          => json
        case head :: tail => builder(tail, json :+ s""""$count":"${head.options.size}"""", count + 1)
      }
    }

    s"{${builder(feedForm.questions, Nil, 0).mkString(",")}}"
  }

}
