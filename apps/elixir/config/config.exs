import Config

# codepagex は既定で一部の符号化表しか組み込まない。
# 第 9 章が読む Shift_JIS（CP932）の CSV のために、明示して組み込む。
config :codepagex, :encodings, ["VENDORS/MICSFT/WINDOWS/CP932"]
