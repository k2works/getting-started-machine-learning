# frozen_string_literal: true

require "csv"

module GettingStartedMl
  # 第 2 章の表と行。
  module Chapter02
    # CSV の 1 行。セルの文字列を列名で引けるように持つ。空欄は欠損値として扱う。
    class Row
      attr_reader :cells

      def initialize(cells)
        @cells = cells
      end

      # 数値の列を読む。空欄なら nil を返す。数値として読めなければ ArgumentError を投げる。
      def number(column)
        cell = text(column).strip
        return nil if cell.empty?

        Float(cell)
      rescue ArgumentError
        raise ArgumentError, "#{column} を数値として読めません: #{cell}"
      end

      # 文字列の列を読む。列が無ければ KeyError を投げる。
      def text(column)
        cells.fetch(column) { raise KeyError, "列がありません: #{column}" }
      end

      # セルが空欄かどうかを返す。
      def missing?(column)
        text(column).strip.empty?
      end

      def ==(other)
        other.is_a?(Row) && cells == other.cells
      end
    end

    # CSV の表。列の順と行を持つ。
    Table = Data.define(:columns, :rows) do
      # CSV を読み込んで表にする。CSV.read はファイルを開くときに BOM を取り除く。
      # 空欄は nil になるので、欠損値を空文字列にそろえてから行にする。
      def self.load(csv_file)
        csv = CSV.read(csv_file, headers: true)
        rows = csv.map { |record| Row.new(record.to_h.transform_values(&:to_s)) }

        new(columns: csv.headers, rows:)
      end

      # 列ごとに欠損値の数を数える。Hash は挿入の順を保つので、列の順は表の列の順のまま。
      def count_missing
        columns.to_h { |column| [column, rows.count { |row| row.missing?(column) }] }
      end
    end
  end
end
