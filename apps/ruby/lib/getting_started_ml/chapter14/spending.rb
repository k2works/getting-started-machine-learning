# frozen_string_literal: true

module GettingStartedMl
  # 第 14 章の卸売業者の顧客ごとの支出額（Wholesale.csv）。
  module Chapter14
    # 区分を表す番号で、支出額ではない列。
    CATEGORIES = %w[Channel Region].freeze

    # 1 つのクラスタの特徴。クラスタ番号、所属する件数、列の順のままの列ごとの平均。
    ClusterSummary = Data.define(:cluster, :count, :means)

    module_function

    # Channel と Region を除いた支出額の列を読み込む。欠損値があれば失敗にする。
    def load_spending(csv_file)
      table = Chapter02::Table.load(csv_file)
      columns = table.columns - CATEGORIES

      table.rows.map do |row|
        values = columns.map { |column| row.number(column) || raise(ArgumentError, "欠損値があります: #{column}") }
        Chapter02::Features.new(columns:, values:)
      end
    end

    # 第 9 章の標準化（件数で割る標準偏差）で列ごとにそろえ、1 件を 1 行とする行列にする。
    def standardize_spending(x)
      Numo::DFloat.cast(Chapter09::Standardizer.fit(x).transform(x).map(&:values))
    end

    # クラスタごとの件数と、元の単位での列ごとの平均を、件数の多い順に並べる。件数が同じなら番号の順。
    def summarize_clusters(x, labels)
      summaries = x.zip(labels).group_by(&:last).map { |cluster, members| summarize(cluster, members.map(&:first)) }

      summaries.sort_by { |summary| [-summary.count, summary.cluster] }
    end

    # 1 つのクラスタに属する特徴量の並びを要約する。
    def summarize(cluster, members)
      means = members.map(&:values).transpose.map { |column| column.sum / column.size }

      ClusterSummary.new(cluster:, count: members.size, means:)
    end

    # クラスタの要約を、タブで区切った 1 行にする。平均は整数に丸める（Rust 版の {:.0} と同じく偶数丸め）。
    def format_summary(summary)
      [summary.cluster, summary.count, *summary.means.map { |mean| format("%.0f", mean) }].join("\t")
    end
  end
end
