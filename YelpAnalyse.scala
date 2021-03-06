import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.ml.feature.StringIndexer
import org.apache.spark.ml.feature.StopWordsRemover
import org.apache.spark.ml.Pipeline
import org.apache.spark.ml.tuning.{CrossValidator, CrossValidatorModel, ParamGridBuilder}
import org.apache.spark.ml.evaluation.MulticlassClassificationEvaluator
import org.apache.spark.sql.functions._
import org.apache.spark.ml.classification.{DecisionTreeClassifier, LogisticRegression, NaiveBayes, RandomForestClassifier}
import org.apache.spark.ml.feature.CountVectorizer
import org.apache.spark.ml.feature.{HashingTF, IDF, Tokenizer}
import org.apache.spark.ml.feature.Word2Vec

object YelpAnalyse {
  def main(args: Array[String]): Unit = {
    if (args.length != 5) {
      println("Usage: YelpAnalyse 0.InputDir 1.polarity/stars 2.OutputDir 3.numofFold 4.numofHoldout")
      sys.exit(1)
    }

    if (!(args(1) == "polarity" || args(1) == "stars")){
      println("Usage: YelpAnalyse InputDir polarity/stars OutputDir")
      sys.exit(1)
    }

    val numOfFold = args(3).toInt
    val numOfHoldout = args(4).toInt

    // create Spark Session
    val spark = SparkSession
      .builder()
      .appName("YelpAnalyse")
      .getOrCreate()
      //.master("local")



    val sc = spark.sparkContext
    import spark.implicits._

    // read dataSet
    val df = spark.read.json(args(0))

    // drop rows that text field is null
    val df_clean = df.na.drop(Seq("text"))

    // Add column polarity
    val polarity = when($"stars" <= 2, "negative")
      .when($"stars" >= 4, "positive")
      .otherwise("neutral")
    val df_polarity = df_clean.withColumn("polarity", polarity)


    var labelIndexer = new StringIndexer().setInputCol(args(1)).setOutputCol("label")
    // convert polarity to label


    // tokenizing "text" to list(words)
    val tokenizer = new Tokenizer().setInputCol("text").setOutputCol("words")

    // remove stop words
    val remover = new StopWordsRemover()
      .setInputCol("words")
      .setOutputCol("words_filtered")

    ///////////////////////// convert words to features
    val hashingTF = new HashingTF()
      .setInputCol(remover.getOutputCol)
      .setOutputCol("rawFeatures")
    val idf = new IDF().setInputCol("rawFeatures").setOutputCol("features")

    val cvModel = new CountVectorizer()
      .setInputCol("words")
      .setOutputCol("features")

    val word2Vec = new Word2Vec()
      .setInputCol("words")
      .setOutputCol("features")

    ///////////////////// classifier model
    val nb = new NaiveBayes()
      .setLabelCol("label")
      .setFeaturesCol("features")

    val lr = new LogisticRegression()
      .setMaxIter(15)
      .setLabelCol("label")
      .setFeaturesCol("features")

    val dt = new DecisionTreeClassifier()
      .setLabelCol("label")
      .setFeaturesCol("features")

    val rf = new RandomForestClassifier()
      .setLabelCol("label")
      .setFeaturesCol("features")

    ///////////////////////////////// Creating pipeline
    var pipeline_array:Array[Pipeline] = Array()
    var pipeline_array_label:Array[String] = Array()
    // Navie Bayes
    val pipeline_hashingTF_IDF_nb = new Pipeline()
      .setStages(Array(labelIndexer, tokenizer, remover, hashingTF,idf, nb))
    pipeline_array = pipeline_array :+ pipeline_hashingTF_IDF_nb
    pipeline_array_label = pipeline_array_label :+ "HashingTF-IDF and Naive Bayes"

    val pipeline_cvModel_nb = new Pipeline()
      .setStages(Array(labelIndexer, tokenizer, remover, cvModel, nb))
    pipeline_array = pipeline_array :+ pipeline_cvModel_nb
    pipeline_array_label = pipeline_array_label :+ "CvModel and Naive Bayes"

    // Logistic Regression
    val pipeline_hashingTF_IDF_lr = new Pipeline()
      .setStages(Array(labelIndexer, tokenizer, remover, hashingTF,idf, lr))
    pipeline_array = pipeline_array :+ pipeline_hashingTF_IDF_lr
    pipeline_array_label = pipeline_array_label :+ "HashingTF-IDF and Logistic Regression"

    val pipeline_cvModel_lr = new Pipeline()
      .setStages(Array(labelIndexer, tokenizer, remover, cvModel, lr))
    pipeline_array = pipeline_array :+ pipeline_cvModel_lr
    pipeline_array_label = pipeline_array_label :+ "CvModel and Logistic Regression"

    val pipeline_word2Vec_lr = new Pipeline()
      .setStages(Array(labelIndexer, tokenizer, remover, word2Vec, lr))
    pipeline_array = pipeline_array :+ pipeline_word2Vec_lr
    pipeline_array_label = pipeline_array_label :+ "Word2Vec and Logistic Regression"

    // Decision Tree
    val pipeline_hashingTF_IDF_dt = new Pipeline()
      .setStages(Array(labelIndexer, tokenizer, remover, hashingTF,idf, dt))
    pipeline_array = pipeline_array :+ pipeline_hashingTF_IDF_dt
    pipeline_array_label = pipeline_array_label :+ "HashingTF-IDF and Decision Tree"

    val pipeline_cvModel_dt = new Pipeline()
      .setStages(Array(labelIndexer, tokenizer, remover, cvModel, dt))
    pipeline_array = pipeline_array :+ pipeline_cvModel_dt
    pipeline_array_label = pipeline_array_label :+ "CvModel and Decision Tree"

    val pipeline_word2Vec_dt = new Pipeline()
      .setStages(Array(labelIndexer, tokenizer, remover, word2Vec, dt))
    pipeline_array = pipeline_array :+ pipeline_word2Vec_dt
    pipeline_array_label = pipeline_array_label :+ "Word2Vec and Decision Tree"

    // Random Forest
    val pipeline_hashingTF_IDF_rf = new Pipeline()
      .setStages(Array(labelIndexer, tokenizer, remover, hashingTF,idf, rf))
    pipeline_array = pipeline_array :+ pipeline_hashingTF_IDF_rf
    pipeline_array_label = pipeline_array_label :+ "HashingTF-IDF and Random Forest"
    val pipeline_cvModel_rf = new Pipeline()
      .setStages(Array(labelIndexer, tokenizer, remover, cvModel, rf))
    pipeline_array = pipeline_array :+ pipeline_cvModel_rf
    pipeline_array_label = pipeline_array_label :+ "CvModel and Random Forest"
    val pipeline_word2Vec_rf = new Pipeline()
      .setStages(Array(labelIndexer, tokenizer, remover, word2Vec, rf))
    pipeline_array = pipeline_array :+ pipeline_word2Vec_rf
    pipeline_array_label = pipeline_array_label :+ "Word2Vec and Random Forest"

    //////////////////////////create paramGrid
    val paramGrid_hashingTF_IDF = new ParamGridBuilder()
      .addGrid(hashingTF.numFeatures, Array(10, 100))
      .build()

    val paramGrid_cvModel = new ParamGridBuilder()
      .addGrid(cvModel.vocabSize, Array(10, 100))
      .build()

    val paramGrid_word2Vec = new ParamGridBuilder()
      .addGrid(word2Vec.vectorSize, Array(10, 100))
      .build()

    val paramGrid_hashingTF_IDF_lr = new ParamGridBuilder()
      .addGrid(hashingTF.numFeatures, Array(10, 100))
      .addGrid(lr.regParam, Array(0.0,1.0))
      .addGrid(lr.elasticNetParam, Array(0.0, 0.4))
      .build()

    val paramGrid_cvModel_lr = new ParamGridBuilder()
      .addGrid(cvModel.vocabSize, Array(10, 100))
      .addGrid(lr.regParam, Array(0.0,1.0))
      .addGrid(lr.elasticNetParam, Array(0.0, 0.4))
      .build()

    val paramGrid_word2Vec_lr = new ParamGridBuilder()
      .addGrid(word2Vec.vectorSize, Array(10, 100))
      .addGrid(lr.regParam, Array(0.0,1.0))
      .addGrid(lr.elasticNetParam, Array(0.0, 0.4))
      .build()

    val paramGrid_hashingTF_IDF_rf = new ParamGridBuilder()
      .addGrid(hashingTF.numFeatures, Array(10, 100))
      .addGrid(rf.numTrees, Array(10, 50, 100))
      .build()

    val paramGrid_cvModel_rf = new ParamGridBuilder()
      .addGrid(cvModel.vocabSize, Array(10, 100))
      .addGrid(rf.numTrees, Array(10, 50, 100))
      .build()

    val paramGrid_word2Vec_rf = new ParamGridBuilder()
      .addGrid(word2Vec.vectorSize, Array(10, 100))
      .addGrid(rf.numTrees, Array(10, 50, 100))
      .build()

    val paramGrid_array = Array(paramGrid_hashingTF_IDF, paramGrid_cvModel, paramGrid_hashingTF_IDF_lr,
      paramGrid_cvModel_lr, paramGrid_word2Vec_lr, paramGrid_hashingTF_IDF, paramGrid_cvModel, paramGrid_word2Vec,
      paramGrid_hashingTF_IDF_rf, paramGrid_cvModel_rf, paramGrid_word2Vec_rf)

    ///////////////////// Create model
    var model_array:Array[CrossValidator] = Array()
    // pipeline_array
    // pipeline_array_label
    for ( i <- 0 to (pipeline_array.length - 1)){
      val model = new CrossValidator()
        .setEstimator(pipeline_array(i))
        .setEvaluator(new MulticlassClassificationEvaluator)
        .setEstimatorParamMaps(paramGrid_array(i))
        .setNumFolds(numOfFold)  // Use 3+ in practice
      model_array = model_array :+ model
    }

    ///////////////////// running training and testing models
    for(runtime <- 1 to numOfHoldout){
      // split data into train and test
      val Array(train, test) = df_polarity.randomSplit(Array(0.7, 0.3))
      // fit train data
      var fitted_models:Array[CrossValidatorModel] = Array()
      for (i <- 0 to (model_array.length - 1)){
        fitted_models = fitted_models :+ model_array(i).fit(train)
      }

      // Run test
      var test_models:Array[DataFrame] = Array()
      for (i <- 0 to (model_array.length - 1)){
        test_models = test_models :+ fitted_models(i).transform(test)
      }

      //evaluate results
      val evaluator = new MulticlassClassificationEvaluator()
      evaluator.setLabelCol("label")

      val evaluator_array = Array("f1", "weightedPrecision", "weightedRecall", "accuracy")
      var evaluator_result:Array[Double] = Array()

      for (eval <- 0 to (evaluator_array.length - 1)){
        evaluator.setMetricName(evaluator_array(eval))
        for (j <- 0 to (model_array.length - 1)){
          evaluator_result = evaluator_result :+ evaluator.evaluate(test_models(j))
        }
      }
      // outpu result
      var result = ""

      for (j <- 0 to (evaluator_result.length - 1)){
        if (j % model_array.length == 0){
          result = result.concat("\n" + evaluator_array(j / model_array.length) + " values result: \n")
        }
        val k = j % model_array.length
        result = result.concat(pipeline_array_label(k) + ": \t" + evaluator_result(j) + "\n")
      }

      val result_rdd = sc.parallelize(Seq(result))
      result_rdd.saveAsTextFile(args(2) + "/output" + runtime)

    }

  }

}
