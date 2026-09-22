# frozen_string_literal: true

require "test_helper"

class Chapter14RumaleTest < Minitest::Test
  C = GettingStartedMl::Chapter14

  def three_pairs
    Numo::DFloat.cast([[0.0], [1.0], [10.0], [11.0], [20.0], [21.0]])
  end

  # Rumale の KMeans が学習後に持つのは中心だけで、SSE（scikit-learn の inertia_）は無い
  def test_RumaleのKMeansはSSEを持たない
    model = Rumale::Clustering::KMeans.new(n_clusters: 3, random_seed: 0).fit(three_pairs)

    refute_respond_to model, :inertia
    refute_respond_to model, :sse
    assert_equal [3, 1], model.cluster_centers.shape
  end

  def test_Rumaleの既定はk_means_plus_plusで繰り返しは五十回まで
    params = Rumale::Clustering::KMeans.new(random_seed: 0).params

    assert_equal({ init: "k-means++", max_iter: 50, tol: 1e-4 }, params.slice(:init, :max_iter, :tol))
  end

  def test_Rumaleの中心から自作と同じ式でSSEを測る
    result = C::RumaleKMeans.fit(three_pairs, 3, seed: 0, n_init: 10)

    assert_in_delta 1.5, result.sse, 1e-12
  end

  def test_同じシードなら同じ結果になる
    first = C::RumaleKMeans.fit(three_pairs, 2, seed: 3, n_init: 1)
    second = C::RumaleKMeans.fit(three_pairs, 2, seed: 3, n_init: 1)

    assert_equal first.centers.to_a, second.centers.to_a
  end
end
