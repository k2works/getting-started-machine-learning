# 実データ（書籍の購入者だけが使える）が無い環境では、:data のテストを外す。
exclude = if File.dir?(GettingStartedMl.Dataset.dir()), do: [], else: [:data]

unless exclude == [] do
  IO.puts("学習データが見つからないので :data のテストを外します（#{GettingStartedMl.Dataset.dir()}）")
end

ExUnit.start(exclude: exclude)
