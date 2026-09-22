# frozen_string_literal: true

module GettingStartedMl
  # 第 9 章のダミー変数。
  module Chapter09
    module_function

    # 欠損値（空欄）を除いたカテゴリを辞書順に並べ、先頭を除いて返す
    # （pandas の get_dummies(drop_first: True) と同じ）。
    def categories(values)
      values.map(&:strip).reject(&:empty?).uniq.sort.drop(1)
    end

    # 列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。
    # 値が一致すれば "1"、それ以外は "0"。元の表は変えずに新しい表を返す。
    def encode(table, column, categories)
      dummy_columns = categories.map { |category| "#{column}_#{category}" }
      rows = table.rows.map { |row| Chapter02::Row.new(encode_cells(row, column, categories)) }

      Chapter02::Table.new(columns: (table.columns - [column]) + dummy_columns, rows:)
    end

    # 1 行のセルから列を除き、ダミー変数のセルを加える。
    def encode_cells(row, column, categories)
      value = row.text(column).strip
      dummies = categories.to_h { |category| ["#{column}_#{category}", value == category ? "1" : "0"] }

      row.cells.except(column).merge(dummies)
    end
  end
end
