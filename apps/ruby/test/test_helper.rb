# frozen_string_literal: true

require "simplecov"
SimpleCov.start do
  add_filter "/test/"
  add_filter "/vendor/"
end

require "minitest/autorun"
require "getting_started_ml"
