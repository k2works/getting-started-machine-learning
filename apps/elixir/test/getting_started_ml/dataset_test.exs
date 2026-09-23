defmodule GettingStartedMl.DatasetTest do
  use ExUnit.Case, async: true

  alias GettingStartedMl.Dataset

  describe "学習データの置き場" do
    test "環境変数が無ければ既定の場所を使う" do
      assert Dataset.dir(%{}) == "../data/sukkiri-ml"
    end

    test "環境変数があればその場所を使う" do
      assert Dataset.dir(%{"ML_DATA_DIR" => "/tmp/data"}) == "/tmp/data"
    end

    test "環境変数が空なら既定の場所を使う" do
      assert Dataset.dir(%{"ML_DATA_DIR" => ""}) == "../data/sukkiri-ml"
    end

    test "引数を省略すると実際の環境変数を読む" do
      assert is_binary(Dataset.dir())
    end
  end
end
