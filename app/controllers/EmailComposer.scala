package controllers

import java.util.Date

import models.SessionInfo

object EmailComposer {

  val url: String = routes.FeedbackFormsResponseController.getFeedbackFormsForToday().url
  val feedbackUrl = s"""http://localhost:9000$url"""

  def reminderMailBody(sessions: List[SessionInfo]): String =
    s"""<p>Hi All,</p>
       |<p>A gentle reminder, please share your feedback regarding the following session(s) latest by the <strong>end of the day</strong>.</p>
       |<ul>${composeSessionList(sessions: List[SessionInfo])}</ul>
       |<p>Your feedback is valuable for us, so please cooperate and share your feedback <a href="$feedbackUrl">here.</a></p>
       |<p><Strong>If you have already shared the feedback kindly ignore this mail.</strong></p>
       |<p>Thanks</p>
       |<p>Admin</p>
    """.stripMargin

  def feedbackMailBody(sessions: List[SessionInfo]): String =
    s"""<p>Hi</p>
       |<p>Please share your feedback regarding following session</p>
       |<ul>${composeSessionList(sessions: List[SessionInfo])}</ul>
       |<p>Your feedback is valuable for us, you can access it here <a href="$feedbackUrl">here.</a></p>
       |<p>Thanks</p>
       |<p>Admin</p>
    """.stripMargin

  def composeSessionList(sessions: List[SessionInfo]): String = sessions
    .map(session => s"<li><strong>${session.topic}</strong> by ${session.email} held on ${new Date(session.date.value)}</li>").mkString

}
