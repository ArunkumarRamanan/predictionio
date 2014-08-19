package io.prediction.examples.itemrank

import io.prediction.controller.LServing
import io.prediction.controller.EmptyParams
import breeze.stats.{ mean, meanAndVariance }

// Only return first prediction
class ItemRankServing extends LServing[EmptyParams, Query, Prediction] {
  override def serve(query: Query, predictions: Seq[Prediction]): Prediction = {
    predictions.head
  }
}

// Return average item score for each non-original prediction.
class ItemRankAverageServing extends LServing[EmptyParams, Query, Prediction] {
  override def serve(query: Query, prediction: Seq[Prediction]): Prediction = {
    // Only consider non-original items
    val itemsList: Seq[Seq[(String, Double)]] = prediction
      .filter(!_.isOriginal)
      .map(_.items)

    val validCount = itemsList.size
    if (validCount == 0) {
      return new Prediction(
        items = query.items.map(e => (e, 0.0)),
        isOriginal = true)
    }

    // mvc : mean, variance, count
    val mvcList: Seq[(Double, Double, Long)] = itemsList
      .map { l => meanAndVariance(l.map(_._2)) }

    val means = mvcList.map(_._1)
    val variances = mvcList.map(_._2)
    val counts = mvcList.map(_._3)

    val stdevs = variances.map(v => math.sqrt(v))

    val querySize = query.items.size

    val items: Seq[(String, Double)] = (0 until validCount)
    .flatMap { i =>
      val items = itemsList(i)
      if (items.size != querySize) {
        throw new Exception(
          s"Prediction $i has size ${items.size} != query $querySize")
      }

      val mean = means(i)
      val stdev = stdevs(i)

      items.map(e => (e._1, (e._2 - mean) / stdev))
    }
    .groupBy(_._1)  // group by item
    .mapValues(l => mean(l.map(_._2)))
    .toSeq
    .sortBy(-_._2)

    new Prediction(items, false)
  }
}