# frozen_string_literal: true

require "json"
require "sinatra/base"

module GettingStartedMl
  module Chapter15
    # プレゼンテーション層。Sinatra のルートと、例外の HTTP ステータスコードへの変換。
    #
    # Sinatra::Base を継承したモジュール形式で書く（トップレベルの get・post を使うクラシック形式は
    # Object にメソッドを足すので、ライブラリの中では使わない）。
    class Api < Sinatra::Base
      # JSON として読めない・型が合わない入力に返す理由。JSON::ParserError の文言は応答に出さない。
      INVALID_JSON = "JSON の形式または値の型が正しくありません"
      # パスごとに許すメソッド。ほかのメソッドには 405 を返す。
      ALLOWED_METHODS = { "/health" => "GET", "/cinema/sales" => "POST", "/survived" => "POST" }.freeze

      # 開発時の例外画面と、Host ヘッダーの検査（Sinatra 4.1 から開発環境で既定になった）を切る。
      # 例外は各ルートで自分でステータスコードに変える。
      set :show_exceptions, false
      set :raise_errors, false
      set :dump_errors, false
      set :host_authorization, { permitted_hosts: [] }

      # 予測サービスを差し込んだアプリケーションを作る。Sinatra::Base.new は Rack のミドルウェアで包んで返す。
      def initialize(app = nil, service:)
        super(app)
        @service = service
      end

      before { content_type :json }

      get "/health" do
        health = @service.health
        status = health.all?(&:ready) ? "ok" : "degraded"

        JSON.generate({ status:, models: health.to_h { |model| [model.name, model.ready] } })
      end

      post "/cinema/sales" do
        predict(Validation::MOVIE_TYPES, Validation.method(:movie)) do |movie|
          { sales: @service.predict_sales(movie) }
        end
      end

      post "/survived" do
        predict(Validation::PASSENGER_TYPES, Validation.method(:passenger)) do |passenger|
          { survived: @service.survives?(passenger) }
        end
      end

      # ルートが無いときは、パスを知っていれば 405、知らなければ 404 にする。
      not_found do
        allowed = ALLOWED_METHODS[request.path_info]
        return JSON.generate({ detail: "見つかりません" }) if allowed.nil?

        status 405
        headers "Allow" => allowed
        JSON.generate({ detail: "許していないメソッドです" })
      end

      private

      # 本文を読んで検証し、正しければブロックで予測する。失敗はステータスコードに変える。
      def predict(types, validator)
        request_json = Validation.read_json(request.body.read, types)
        return rejected([INVALID_JSON]) if request_json.nil?

        validated = validator.call(request_json)
        return rejected(validated.errors) unless validated.valid?

        JSON.generate(yield(validated.value))
      rescue ModelNotFound => e
        failure(503, e.message)
      rescue StandardError
        failure(500, "予測できませんでした")
      end

      # 検証で弾いたときの応答。
      def rejected(errors)
        status 422
        JSON.generate({ detail: errors })
      end

      # 予測に失敗したときの応答。
      def failure(code, detail)
        status code
        JSON.generate({ detail: })
      end
    end
  end
end
