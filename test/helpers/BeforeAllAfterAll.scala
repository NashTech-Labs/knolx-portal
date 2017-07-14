package helpers

import org.specs2.mutable.SpecificationLike
import org.specs2.specification.core.Fragments

trait BeforeAllAfterAll {
  self: SpecificationLike =>

  override def map(fragments: => Fragments): Fragments =
    step(beforeAll()) ^ fragments ^ step(afterAll())

  protected def beforeAll(): Unit = {}

  protected def afterAll(): Unit = {}

}
