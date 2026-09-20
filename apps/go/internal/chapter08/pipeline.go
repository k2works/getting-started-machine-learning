package chapter08

import (
	"fmt"
	"slices"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
)

// Pipeline は前処理を順に Fit・Transform してから、モデルを学習する。
type Pipeline struct {
	Transformers []Transformer
	Model        DecisionTreeClassifier
}

// BuildPipeline は Survived.csv 用のパイプラインを作る。
// 年齢・港の補完とダミー変数化の後に、決定木で分類する。
func BuildPipeline(maxDepth int, classWeight ClassWeight) Pipeline {
	return Pipeline{
		Transformers: []Transformer{
			GroupMedianImputer{Column: "Age", By: []string{"Pclass", "Sex"}},
			MostFrequentImputer{Column: "Embarked"},
			DummyEncoder{Columns: []string{"Sex", "Embarked"}},
		},
		Model: DecisionTreeClassifier{MaxDepth: maxDepth, ClassWeight: classWeight},
	}
}

// Fit は訓練データで前処理とモデルを学習する。
// 前処理は、前の前処理で変換したデータで Fit する。
func (p Pipeline) Fit(x chapter02.Table, t []int) (*FittedPipeline, error) {
	fitted := make([]FittedTransformer, 0, len(p.Transformers))
	prepared := x

	for _, transformer := range p.Transformers {
		fittedTransformer, err := transformer.Fit(prepared)
		if err != nil {
			return nil, err
		}

		fitted = append(fitted, fittedTransformer)

		prepared, err = fittedTransformer.Transform(prepared)
		if err != nil {
			return nil, err
		}
	}

	features, err := ToFeatures(prepared)
	if err != nil {
		return nil, err
	}

	model, err := p.Model.Fit(features, t)
	if err != nil {
		return nil, err
	}

	return &FittedPipeline{Transformers: fitted, Model: model}, nil
}

// FittedPipeline は学習済みの前処理とモデル。予測するときは前処理の Transform だけを使う。
type FittedPipeline struct {
	Transformers []FittedTransformer
	Model        *FittedDecisionTree
}

// Transform は学習済みの前処理を順に適用する。
func (f *FittedPipeline) Transform(x chapter02.Table) (chapter02.Table, error) {
	transformed := x

	for _, transformer := range f.Transformers {
		var err error

		transformed, err = transformer.Transform(transformed)
		if err != nil {
			return chapter02.Table{}, err
		}
	}

	return transformed, nil
}

// Features は前処理をして、モデルに渡す特徴量にする。
func (f *FittedPipeline) Features(x chapter02.Table) ([]chapter02.Features, error) {
	transformed, err := f.Transform(x)
	if err != nil {
		return nil, err
	}

	return ToFeatures(transformed)
}

// Predict は前処理をしてから、1 行ごとのラベル（1 が生存、0 が死亡）を予測する。
func (f *FittedPipeline) Predict(x chapter02.Table) ([]int, error) {
	features, err := f.Features(x)
	if err != nil {
		return nil, err
	}

	return f.Model.Predict(features)
}

// ToFeatures は前処理の済んだ表を特徴量にする。欠損値が残っていれば失敗する。
func ToFeatures(x chapter02.Table) ([]chapter02.Features, error) {
	features := make([]chapter02.Features, 0, len(x.Rows))

	for _, row := range x.Rows {
		values := make([]float64, len(x.Columns))

		for i, column := range x.Columns {
			value, ok, err := row.Number(column)
			if err != nil {
				return nil, err
			}

			if !ok {
				return nil, fmt.Errorf("欠損値が残っています: %s", column)
			}

			values[i] = value
		}

		converted, err := chapter02.NewFeatures(slices.Clone(x.Columns), values)
		if err != nil {
			return nil, err
		}

		features = append(features, converted)
	}

	return features, nil
}
