package chapter08;

/** クラスの重みの付け方。 */
public enum ClassWeight {
  /** 重みを付けない（すべて 1） */
  NONE,
  /** クラスの件数に反比例する重みを付ける */
  BALANCED,
}
