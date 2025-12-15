package com.gdpark.ffmpeg.util;

import smile.data.formula.Formula;
import smile.regression.RandomForest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;

import static smile.io.Read.arff;

public class Smile {
  /**
   * Trains a Random Forest classifier on the bundled "iris.arff" dataset and prints its evaluation metrics.
   *
   * Loads the "iris.arff" resource, fits a RandomForest using the dataset's `class` attribute as the target,
   * then writes the model's metrics to standard output.
   *
   * @throws IOException if the ARFF resource cannot be read
   * @throws ParseException if the ARFF content cannot be parsed
   * @throws URISyntaxException if the ARFF resource URI is malformed
   */
  public void smileRun() throws IOException, ParseException, URISyntaxException {
    var iris = arff("iris.arff");

    // 랜덤 포레스트 모델 학슴
    var model = RandomForest.fit(Formula.lhs("class"), iris);

    // 결과 출력
    System.out.println("모델 정확도 및 지표");
    System.out.println(model.metrics());
  }
}