package com.gdpark.ffmpeg;

import smile.data.formula.Formula;
import smile.regression.RandomForest;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.ParseException;

import static smile.io.Read.arff;

public class Smile {
    void smileRun () throws IOException, ParseException, URISyntaxException {
        var iris = arff("iris.arff");

        // 랜덤 포레스트 모델 학슴
        var model = RandomForest.fit(Formula.lhs("class"), iris);

        // 결과 출력
        System.out.println("모델 정확도 및 지표");
        System.out.println(model.metrics());
    }
}
