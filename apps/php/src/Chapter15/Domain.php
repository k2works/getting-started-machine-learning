<?php

declare(strict_types=1);

namespace GettingStartedMl\Chapter15;

/** この章のドメインの名前。モデルの名前と、保存するファイルの名前を決める。 */
final class Domain
{
    /** 興行収入のモデルの名前。 */
    public const string SALES_MODEL = 'cinema';

    /** 生存予測のモデルの名前。 */
    public const string SURVIVAL_MODEL = 'survived';

    /** モデルの名前を、置き場の順で並べたもの。ヘルスチェックの並びもこれに従う。 */
    public const array MODEL_NAMES = [self::SALES_MODEL, self::SURVIVAL_MODEL];
}
