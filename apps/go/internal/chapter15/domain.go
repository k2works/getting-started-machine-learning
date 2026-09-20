// Package chapter15 は、学習済みのモデルを HTTP の予測 API として公開する。
// 層（ドメイン・サービス・置き場・API）を分け、依存の向きを内側へそろえる。
package chapter15

import (
	"errors"
	"fmt"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter02"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter07"
	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter08"
)

// ErrModelNotFound はモデルを読み込めないことを表す番兵のエラー。
// 呼び出し側は errors.Is で判別し、API では 503 に変える。
var ErrModelNotFound = errors.New("モデルがありません")

// ModelNotFound は、どのモデルが無いのかを添えたエラーを作る。
func ModelNotFound(name string) error {
	return fmt.Errorf("%w: %s", ErrModelNotFound, name)
}

// Movie は映画の特徴量。SNS の評判 2 種類・主演の人気・原作の有無（0 か 1）。
type Movie struct {
	SNS1     float64
	SNS2     float64
	Actor    float64
	Original int
}

// Features は第 7 章のモデルに渡す特徴量にする。
func (m Movie) Features() (chapter02.Features, error) {
	return chapter02.NewFeatures(
		chapter07.CinemaFeatures,
		[]float64{m.SNS1, m.SNS2, m.Actor, float64(m.Original)},
	)
}

// Passenger は乗客の特徴量。空文字列は欠損値として前処理に任せる。
type Passenger struct {
	Pclass   string
	Sex      string
	Age      string
	SibSp    string
	Parch    string
	Fare     string
	Embarked string
}

// Row は第 8 章のパイプラインに渡す行にする。
func (p Passenger) Row() (chapter02.Row, error) {
	return chapter08.Passenger(p.Pclass, p.Sex, p.Age, p.SibSp, p.Parch, p.Fare, p.Embarked)
}

// SalesModel は興行収入を予測する約束。
type SalesModel interface {
	PredictSales(movie Movie) (float64, error)
}

// SurvivalModel は生存を予測する約束。
type SurvivalModel interface {
	Survives(passenger Passenger) (bool, error)
}

// ModelStore は学習済みモデルの置き場の約束。
// 読み込めなければ ErrModelNotFound を包んだエラーを返す。
type ModelStore interface {
	LoadSalesModel() (SalesModel, error)
	LoadSurvivalModel() (SurvivalModel, error)
}

// linearSalesModel は第 7 章の線形回帰のモデルを SalesModel の約束に合わせる。
type linearSalesModel struct {
	model chapter07.LinearModel
}

func (a linearSalesModel) PredictSales(movie Movie) (float64, error) {
	features, err := movie.Features()
	if err != nil {
		return 0, err
	}

	return a.model.Predict(features)
}

// pipelineSurvivalModel は第 8 章の学習済みパイプラインを SurvivalModel の約束に合わせる。
type pipelineSurvivalModel struct {
	pipeline *chapter08.FittedPipeline
}

func (a pipelineSurvivalModel) Survives(passenger Passenger) (bool, error) {
	row, err := passenger.Row()
	if err != nil {
		return false, err
	}

	predictions, err := a.pipeline.Predict(chapter08.FeaturesTable([]chapter02.Row{row}))
	if err != nil {
		return false, err
	}

	return predictions[0] == chapter08.Survived, nil
}
