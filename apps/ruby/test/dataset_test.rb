# frozen_string_literal: true

require "test_helper"

class DatasetTest < Minitest::Test
  def test_環境変数が無ければ既定の場所を使う
    assert_equal File.join("..", "data", "sukkiri-ml"), GettingStartedMl::Dataset.dir({})
  end

  def test_環境変数があればその場所を使う
    assert_equal "/tmp/data", GettingStartedMl::Dataset.dir({ "ML_DATA_DIR" => "/tmp/data" })
  end

  def test_環境変数が空なら既定の場所を使う
    assert_equal File.join("..", "data", "sukkiri-ml"), GettingStartedMl::Dataset.dir({ "ML_DATA_DIR" => "" })
  end
end
