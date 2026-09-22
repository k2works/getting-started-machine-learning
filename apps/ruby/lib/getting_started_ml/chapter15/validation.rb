# frozen_string_literal: true

require "json"

module GettingStartedMl
  module Chapter15
    # プレゼンテーション層の入口。要求の JSON を読み、検証してドメインの値にする。
    #
    # JSON.parse は型を決めずに Hash・Integer・Float・String を返すので、Rust 版の serde が
    # 読み込みと同時にしていた型の確認を、列ごとの型の表で自分で行う。
    module Validation
      # 検証の結果。理由の一覧が空なら正しく、value にドメインの値が入る。
      Validated = Data.define(:value, :errors) do
        def valid?
          errors.empty?
        end
      end

      # 興行収入の予測の要求の列と型。原作の有無は整数で受け取る。
      MOVIE_TYPES = { "sns1" => Numeric, "sns2" => Numeric, "actor" => Numeric, "original" => Integer }.freeze

      # 生存の予測の要求の列と型。
      PASSENGER_TYPES = { "pclass" => Integer, "sex" => String, "age" => Numeric, "sib_sp" => Integer,
                          "parch" => Integer, "fare" => Numeric, "embarked" => String }.freeze

      module_function

      # 本文を JSON のオブジェクトとして読み、列の型を確かめる。読めないか型が合わなければ nil を返す。
      # null は「値が無い」とみなし、表に無い列は無視する。
      def read_json(body, types)
        request = JSON.parse(body)
        return nil unless request.is_a?(Hash)

        request.all? { |field, value| typed?(types[field], value) } ? request : nil
      rescue JSON::ParserError
        nil
      end

      # 値が型に合うかどうか。null と、表に無い列（型が nil）は問わない。
      def typed?(type, value)
        type.nil? || value.nil? || value.is_a?(type)
      end

      # 理由（nil は問題なし）を集め、1 つも無ければブロックで値を作る。
      def validate(reasons)
        errors = reasons.compact

        Validated.new(value: errors.empty? ? yield : nil, errors:)
      end

      # 値が無ければ理由を返す。
      def required(request, field)
        request[field].nil? ? "#{field} は必須です" : nil
      end

      # 値が負であれば理由を返す。値が無ければ何も言わない（required の担当）。
      def not_negative(request, field)
        value = request[field]
        !value.nil? && value.negative? ? "#{field} は 0 以上にしてください" : nil
      end

      # 値が選択肢に無ければ理由を返す。値が無ければ何も言わない。
      def one_of(request, field, allowed)
        value = request[field]
        value.nil? || allowed.include?(value) ? nil : "#{field} は #{allowed.join('、')} のどれかにしてください"
      end

      # 検証して、正しければ映画の特徴量にする。
      def movie(request)
        fields = %w[sns1 sns2 actor original]
        reasons = fields.map { |field| required(request, field) } +
                  %w[sns1 sns2 actor].map { |field| not_negative(request, field) } +
                  [one_of(request, "original", [0, 1])]

        validate(reasons) { Movie.new(**fields.to_h { |field| [field.to_sym, request[field]] }) }
      end

      # 検証して、正しければ乗客の特徴量にする。省略された列は空文字列（欠損値）にする。
      def passenger(request)
        validate(passenger_reasons(request)) do
          Passenger.new(**PASSENGER_TYPES.keys.to_h { |field| [field.to_sym, request[field].to_s] })
        end
      end

      # 乗客の要求の理由。年齢と乗船港は省略できる。
      def passenger_reasons(request)
        %w[pclass sex sib_sp parch fare].map { |field| required(request, field) } +
          [one_of(request, "pclass", [1, 2, 3]), one_of(request, "sex", %w[female male])] +
          %w[age sib_sp parch fare].map { |field| not_negative(request, field) } +
          [one_of(request, "embarked", %w[C Q S])]
      end
    end
  end
end
