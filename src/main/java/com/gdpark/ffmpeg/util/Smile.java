package com.gdpark.ffmpeg.util;

import smile.data.formula.Formula;
import smile.regression.RandomForest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;

import static smile.io.Read.arff;

public class Smile {
  /**
   * 내장된 "iris.arff" 데이터셋으로 랜덤 포레스트 분류기를 학습시키고 평가 지표를 출력합니다.
   *
   * <p>"iris.arff" 리소스를 로드하고 `class` 속성을 타겟으로 하여 랜덤 포레스트를 학습시킨 후, 모델의 성능 평가 결과를 표준 출력으로 인쇄합니다.
   *
   * @throws IOException ARFF 리소스를 읽을 수 없는 경우
   * @throws ParseException ARFF 내용을 파싱할 수 없는 경우
   * @throws URISyntaxException ARFF 리소스 URI 형식이 잘못된 경우
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
