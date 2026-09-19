package chapter15;

/** 学習済みモデルが見つからないことを表す。メッセージにはファイルのパスを含めない。 */
public final class ModelNotFoundException extends Exception {
  private static final long serialVersionUID = 1L;

  private final String model;

  public ModelNotFoundException(String model) {
    super("学習済みモデル " + model + " が見つかりません");
    this.model = model;
  }

  /** 見つからなかったモデルの名前。 */
  public String model() {
    return model;
  }
}
