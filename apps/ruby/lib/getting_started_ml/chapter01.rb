# frozen_string_literal: true

require "csv"

module GettingStartedMl
  # 第 1 章: 人間が決めたルールできのこ派・たけのこ派を判定する。
  module Chapter01
    # 「20 代ならきのこ派」というルールの年代。
    KINOKO_AGE_GROUP = 20

    # きのこ派の呼び名。
    KINOKO = "きのこ"
    # たけのこ派の呼び名。
    TAKENOKO = "たけのこ"

    # 学習データ KvsT.csv の 1 行。身長・体重・年代と、正解ラベルの派閥を持つ。
    Person = Data.define(:height, :weight, :age_group, :faction)

    # 判定の手がかりになる特徴量。正解ラベルを持たない。
    Features = Data.define(:height, :weight, :age_group)

    module_function

    # CSV を読み込み、列名で値を取り出して人物のリストにする。
    # CSV.foreach はファイルを開くときに BOM を取り除くので、ほかの言語版のような前処理は要らない。
    def load_people(csv_file)
      CSV.foreach(csv_file, headers: true).map do |row|
        Person.new(
          height: number(row, "身長"),
          weight: number(row, "体重"),
          age_group: number(row, "年代"),
          faction: text(row, "派閥")
        )
      end
    end

    # 列名で数値を読む。整数として読めなければ ArgumentError を投げる。
    def number(row, column)
      cell = text(row, column)
      Integer(cell, 10)
    rescue ArgumentError
      raise ArgumentError, "#{column} を数値として読めません: #{cell}"
    end

    # 列名でセルを読む。列が無ければ KeyError を投げる。
    def text(row, column)
      raise KeyError, "列がありません: #{column}" unless row.headers.include?(column)

      row[column]
    end

    # 人物のリストを特徴量と正解ラベルに分ける。
    def split_features_and_labels(people)
      features = people.map do |person|
        Features.new(height: person.height, weight: person.weight, age_group: person.age_group)
      end

      [features, people.map(&:faction)]
    end

    # 人間が決めたルールで派閥を判定する。
    def predict_by_rule(features)
      features.age_group == KINOKO_AGE_GROUP ? KINOKO : TAKENOKO
    end

    # 予測が正解ラベルと一致した割合を返す。件数が違えば ArgumentError を投げる。
    def accuracy(predictions, labels)
      unless predictions.size == labels.size
        raise ArgumentError, "予測と正解ラベルの件数が違います: #{predictions.size} と #{labels.size}"
      end

      predictions.zip(labels).count { |prediction, label| prediction == label }.fdiv(labels.size)
    end

    # 実データでルールによる判定の正解率を表示する。
    def run(out = $stdout)
      people = load_people(File.join(Dataset.dir, "KvsT.csv"))
      x, t = split_features_and_labels(people)
      predictions = x.map { |features| predict_by_rule(features) }

      out.puts "データ件数: #{people.size}"
      out.puts format("ルールによる判定の正解率: %.4f", accuracy(predictions, t))
    end
  end
end
