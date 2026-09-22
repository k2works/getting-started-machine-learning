# frozen_string_literal: true

require "fileutils"

module GettingStartedMl
  module Chapter08
    # 学習済みのパイプラインを Marshal で保存し、読み込む。
    #
    # Marshal は Data・Hash・クラスのインスタンスをそのままバイト列にできるので、変換のコードが要らない。
    # その代わり、読み込むとバイト列に書かれたクラスのオブジェクトが作られる。信頼できないファイルは読まない。
    module ModelFile
      module_function

      # パイプライン全体（前処理で求めた値とモデル）を保存する。置き場のディレクトリが無ければ作る。
      def save(pipeline, model_file)
        FileUtils.mkdir_p(File.dirname(model_file))
        File.binwrite(model_file, Marshal.dump(pipeline))
      end

      # 保存したパイプラインを読み込む。学習済みのパイプラインでなければ TypeError を投げる。
      def load(model_file)
        pipeline = Marshal.load(File.binread(model_file)) # rubocop:disable Security/MarshalLoad
        raise TypeError, "学習済みのパイプラインではありません: #{pipeline.class}" unless pipeline.is_a?(FittedPipeline)

        pipeline
      end
    end
  end
end
