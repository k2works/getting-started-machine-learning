# frozen_string_literal: true

require "test_helper"

class Chapter13Test < Minitest::Test
  C = GettingStartedMl::Chapter13

  def test_寄与率は指定した数だけ並べる
    assert_equal "PC1 0.5000, PC2 0.3000", C.format_ratios([0.5, 0.3, 0.2], 2)
  end

  def test_係数は小数第三位まで表示する
    loadings = [C::Loading.new(column: "RM", value: 0.1234), C::Loading.new(column: "LSTAT", value: -0.5)]

    assert_equal "RM 0.123, LSTAT -0.500", C.format_loadings(loadings)
  end
end
