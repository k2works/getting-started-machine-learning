# frozen_string_literal: true

require "fileutils"

module GettingStartedMl
  module Chapter15
    # 興行収入のモデルの名前。
    SALES_MODEL = "cinema"
    # 生存予測のモデルの名前。
    SURVIVAL_MODEL = "survived"

    # インフラ層。学習済みモデルをディレクトリのファイルに Marshal で保存し、読み込む置き場。
    #
    # 第 8 章と同じく、読み込むのは自分で保存したファイルだけ（Marshal.load は信頼できるファイルが前提）。
    class FileModelStore
      def initialize(model_dir)
        @model_dir = model_dir
      end

      # 線形回帰のモデルを保存する。置き場のディレクトリが無ければ作る。
      def save_sales_model(model)
        FileUtils.mkdir_p(@model_dir)
        File.binwrite(sales_model_file, Marshal.dump(model))
      end

      # 学習済みパイプラインを保存する（第 8 章の ModelFile.save）。
      def save_survival_model(pipeline)
        Chapter08::ModelFile.save(pipeline, survival_model_file)
      end

      # 線形回帰のモデルを読み込み、興行収入のモデルの約束に合わせて返す。
      def load_sales_model
        model = missing_as_not_found(SALES_MODEL) do
          Marshal.load(File.binread(sales_model_file)) # rubocop:disable Security/MarshalLoad
        end
        raise TypeError, "学習済みの線形回帰ではありません: #{model.class}" unless model.is_a?(Chapter07::LinearModel)

        LinearSalesModel.new(model:)
      end

      # 学習済みパイプラインを読み込み、生存予測のモデルの約束に合わせて返す。
      def load_survival_model
        PipelineSurvivalModel.new(
          pipeline: missing_as_not_found(SURVIVAL_MODEL) { Chapter08::ModelFile.load(survival_model_file) }
        )
      end

      private

      def sales_model_file
        File.join(@model_dir, "#{SALES_MODEL}.dump")
      end

      def survival_model_file
        File.join(@model_dir, "#{SURVIVAL_MODEL}.dump")
      end

      # ファイルが無いことを「モデルがない」に変える。それ以外の失敗はそのまま投げる。
      def missing_as_not_found(name)
        yield
      rescue Errno::ENOENT
        raise ModelNotFound, name
      end
    end
  end
end
