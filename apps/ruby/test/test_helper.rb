# frozen_string_literal: true

require "simplecov"
SimpleCov.start do
  skip "/test/"
  skip "/vendor/"
end

require "tmpdir"
require "minitest/autorun"
require "getting_started_ml"
