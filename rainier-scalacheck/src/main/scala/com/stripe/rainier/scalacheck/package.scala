package com.stripe.rainier.scalacheck

import com.stripe.rainier.core.Generator
import com.stripe.rainier.core.RandomVariable
import com.stripe.rainier.compute.Real
import com.stripe.rainier.compute.Variable
import com.stripe.rainier.sampler.RNG
import com.stripe.rainier.sampler.ScalaRNG

import org.scalacheck.Arbitrary
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Cogen
import org.scalacheck.Gen

object `package` {

  implicit val arbitraryReal: Arbitrary[Real] = Arbitrary(genReal)

  val genReal: Gen[Real] = {

    val genLeafReal: Gen[Real] =
      Gen.oneOf(arbitrary[Double].map(Real(_)), Gen.delay(new Variable))

    val genUnaryRealOp: Gen[Real => Real] =
      Gen.oneOf(arbitrary[Double].map(n => (_: Real).pow(n)),
                Gen.const((_: Real).exp),
                Gen.const((_: Real).log),
                Gen.const((_: Real).abs))

    val genBinaryRealOp: Gen[(Real, Real) => Real] =
      Gen.oneOf((_: Real) + (_: Real),
                (_: Real) * (_: Real),
                (_: Real) - (_: Real),
                (_: Real) / (_: Real),
                (_: Real) > (_: Real),
                (_: Real) < (_: Real),
                (_: Real) >= (_: Real),
                (_: Real) <= (_: Real))

    def genReal(n: Int): Gen[Real] = {
      if (n <= 0) genLeafReal
      else {
        val nn = n - 1
        Gen.frequency(
          (30, genLeafReal),
          (10, genReal(nn).flatMap(x => genUnaryRealOp.map(_(x)))),
          (10,
           genReal(nn).flatMap(x =>
             genReal(nn).flatMap(y => genBinaryRealOp.map(_(x, y)))))
        )
      }
    }

    Gen.sized(genReal)
  }

  implicit def arbitraryGenerator[A: Arbitrary]: Arbitrary[Generator[A]] =
    Arbitrary(genGenerator)

  def genGenerator[A: Arbitrary]: Gen[Generator[A]] = {
    implicit val cogenRNG: Cogen[RNG] =
      Cogen(_ match {
        case ScalaRNG(seed) => seed
        case other          => sys.error(s"$other RNG currently unsupported")
      })

    // note: currently assuming all evaluators are pure and don't have
    // internal state (other than caching)
    implicit val cogenNumericReal: Cogen[Numeric[Real]] =
      Cogen(_.getClass.hashCode.toLong)

    Gen.oneOf(
      arbitrary[A].map(Generator(_)),
      arbitrary[(RNG, Numeric[Real]) => A].map(Generator.from(_)),
      for {
        req <- arbitrary[Set[Real]]
        fn <- arbitrary[(RNG, Numeric[Real]) => A]
      } yield Generator.require(req)(fn)
    )
  }

  implicit def arbitraryRandomVariable[A: Arbitrary]
    : Arbitrary[RandomVariable[A]] =
    Arbitrary(genRandomVariable[A])

  def genRandomVariable[A: Arbitrary]: Gen[RandomVariable[A]] =
    arbitrary[A].flatMap(a =>
      genReal.flatMap(density => RandomVariable(a, density)))

}
