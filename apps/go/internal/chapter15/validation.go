package chapter15

import (
	"fmt"
	"slices"
	"strconv"
	"strings"
)

// Validated は検証の結果。Go には sealed interface が無いので、
// 理由の一覧が空かどうかで「正しい」「不正」を表す構造体にする。
type Validated[T any] struct {
	Value  T
	Errors []string
}

// Valid は理由が 1 つも無いかどうか。
func (v Validated[T]) Valid() bool {
	return len(v.Errors) == 0
}

// validate は理由（空文字列は問題なし）を集め、1 つも無ければ値を作る。
func validate[T any](reasons []string, value func() (T, error)) (Validated[T], error) {
	errs := make([]string, 0, len(reasons))

	for _, reason := range reasons {
		if reason != "" {
			errs = append(errs, reason)
		}
	}

	if len(errs) > 0 {
		return Validated[T]{Errors: errs}, nil
	}

	built, err := value()
	if err != nil {
		return Validated[T]{}, err
	}

	return Validated[T]{Value: built}, nil
}

// required は値が JSON に無ければ理由を返す。
func required(field string, present bool) string {
	if present {
		return ""
	}

	return field + " は必須です"
}

// notNegative は値が負であれば理由を返す。値が無ければ何も言わない（required の担当）。
func notNegative[N float64 | int](field string, value *N) string {
	if value == nil || *value >= 0 {
		return ""
	}

	return field + " は 0 以上にしてください"
}

// oneOf は値が選択肢に無ければ理由を返す。値が無ければ何も言わない。
func oneOf[E comparable](field string, value *E, allowed []E) string {
	if value == nil || slices.Contains(allowed, *value) {
		return ""
	}

	choices := make([]string, len(allowed))
	for i, choice := range allowed {
		choices[i] = fmt.Sprintf("%v", choice)
	}

	slices.Sort(choices)

	return field + " は " + strings.Join(choices, "、") + " のどれかにしてください"
}

// MovieRequest は興行収入の予測の要求。
// Go の数値は 0 が既定値なので、JSON に値が無かったことを表すためにポインタで受ける。
type MovieRequest struct {
	SNS1     *float64 `json:"sns1"`
	SNS2     *float64 `json:"sns2"`
	Actor    *float64 `json:"actor"`
	Original *int     `json:"original"`
}

// originalValues は原作の有無に使える値。
var originalValues = []int{0, 1}

// Validate は検証して、正しければ映画の特徴量にする。
func (r MovieRequest) Validate() (Validated[Movie], error) {
	return validate(
		[]string{
			required("sns1", r.SNS1 != nil),
			required("sns2", r.SNS2 != nil),
			required("actor", r.Actor != nil),
			required("original", r.Original != nil),
			notNegative("sns1", r.SNS1),
			notNegative("sns2", r.SNS2),
			notNegative("actor", r.Actor),
			oneOf("original", r.Original, originalValues),
		},
		func() (Movie, error) {
			return Movie{SNS1: *r.SNS1, SNS2: *r.SNS2, Actor: *r.Actor, Original: *r.Original}, nil
		},
	)
}

// PassengerRequest は生存の予測の要求。年齢と乗船港は省略できる。
type PassengerRequest struct {
	Pclass   *int     `json:"pclass"`
	Sex      *string  `json:"sex"`
	Age      *float64 `json:"age"`
	SibSp    *int     `json:"sib_sp"`
	Parch    *int     `json:"parch"`
	Fare     *float64 `json:"fare"`
	Embarked *string  `json:"embarked"`
}

var (
	// passengerClasses は客室の等級に使える値。
	passengerClasses = []int{1, 2, 3}
	// sexes は性別に使える値。
	sexes = []string{"female", "male"}
	// ports は乗船港に使える値。
	ports = []string{"C", "Q", "S"}
)

// Validate は検証して、正しければ乗客の特徴量にする。省略された列は空文字列（欠損値）にする。
func (r PassengerRequest) Validate() (Validated[Passenger], error) {
	return validate(
		[]string{
			required("pclass", r.Pclass != nil),
			required("sex", r.Sex != nil),
			required("sib_sp", r.SibSp != nil),
			required("parch", r.Parch != nil),
			required("fare", r.Fare != nil),
			oneOf("pclass", r.Pclass, passengerClasses),
			oneOf("sex", r.Sex, sexes),
			notNegative("age", r.Age),
			notNegative("sib_sp", r.SibSp),
			notNegative("parch", r.Parch),
			notNegative("fare", r.Fare),
			oneOf("embarked", r.Embarked, ports),
		},
		func() (Passenger, error) {
			return Passenger{
				Pclass:   strconv.Itoa(*r.Pclass),
				Sex:      *r.Sex,
				Age:      optionalNumber(r.Age),
				SibSp:    strconv.Itoa(*r.SibSp),
				Parch:    strconv.Itoa(*r.Parch),
				Fare:     formatNumber(*r.Fare),
				Embarked: optionalText(r.Embarked),
			}, nil
		},
	)
}

// optionalNumber は省略できる数値をセルの文字列にする。省略されていれば空文字列（欠損値）。
func optionalNumber(value *float64) string {
	if value == nil {
		return ""
	}

	return formatNumber(*value)
}

// formatNumber は数値をセルの文字列にする。
func formatNumber(value float64) string {
	return strconv.FormatFloat(value, 'f', -1, 64)
}

// optionalText は省略できる文字列をセルにする。
func optionalText(value *string) string {
	if value == nil {
		return ""
	}

	return *value
}
