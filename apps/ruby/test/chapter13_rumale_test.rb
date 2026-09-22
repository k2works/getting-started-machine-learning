# frozen_string_literal: true

require "test_helper"

class Chapter13RumaleTest < Minitest::Test
  C = GettingStartedMl::Chapter13

  def x
    Numo::DFloat.cast([[2.5, 2.4, 0.5], [0.5, 0.7, 1.9], [2.2, 2.9, 0.4], [1.9, 2.2, 1.1],
                       [3.1, 3.0, 0.2], [2.3, 2.7, 0.9], [2.0, 1.6, 1.5], [1.0, 1.1, 1.7]])
  end

  # solver に "evd"（固有値分解）を渡すと params には "evd" と残るが、Numo::Linalg が無いので
  # fit で警告を出し、黙って固定小数点法（"fpt"）と同じ計算に落ちる
  def test_Numo_Linalg_が無いとevdを指定しても固定小数点法で解く
    refute defined?(Numo::Linalg)
    evd = Rumale::Decomposition::PCA.new(n_components: 2, solver: "evd", random_seed: 0)
    fpt = Rumale::Decomposition::PCA.new(n_components: 2, solver: "fpt", random_seed: 0).fit(x)

    assert_equal "evd", evd.params[:solver]
    _, warning = capture_io { evd.fit(x) }

    assert_match "Numo::Linalg", warning
    assert_equal fpt.components, evd.components
  end

  # 固定小数点法の既定（max_iter: 100・tol: 1e-4）は向きの cos が 1 に 1e-4 まで近づけば止まるので、
  # 主成分は小数第 2 位でずれる
  def test_Rumaleの既定では主成分が小数第二位でずれる
    gap = (C.fit(x, 3).components - C::RumalePca.fit(x, 3, seed: 0).components).abs.max

    assert_operator gap, :>, 1e-3
  end

  def test_繰り返しを増やして判定を厳しくすれば主成分は自作と一致する
    theirs = C::RumalePca.fit(x, 3, seed: 0, max_iter: 10_000, tol: 1e-12)

    assert_in_delta 0.0, (C.fit(x, 3).components - theirs.components).abs.max, 1e-5
  end

  def test_Rumaleの主成分から求めた寄与率も自作と一致する
    ours = C.fit(x, 3)
    theirs = C::RumalePca.fit(x, 3, seed: 0, max_iter: 10_000, tol: 1e-12)

    ours.explained_variance_ratio.zip(theirs.explained_variance_ratio).each do |mine, other|
      assert_in_delta mine, other, 1e-9
    end
  end

  def test_主成分が一つでも行列にそろえる
    theirs = C::RumalePca.fit(x, 1, seed: 0, max_iter: 10_000, tol: 1e-12)

    assert_equal [1, 3], theirs.components.shape
  end
end
