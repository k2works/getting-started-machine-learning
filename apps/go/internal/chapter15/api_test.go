package chapter15_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/k2works/getting-started-machine-learning/apps/go/internal/chapter15"
)

// stubStore は置き場のスタブ。学習しなくても API の振る舞いを確かめられる。
type stubStore struct {
	sales    chapter15.SalesModel
	survival chapter15.SurvivalModel
}

func (s stubStore) LoadSalesModel() (chapter15.SalesModel, error) {
	if s.sales == nil {
		return nil, chapter15.ModelNotFound(chapter15.SalesModelName)
	}

	return s.sales, nil
}

func (s stubStore) LoadSurvivalModel() (chapter15.SurvivalModel, error) {
	if s.survival == nil {
		return nil, chapter15.ModelNotFound(chapter15.SurvivalModelName)
	}

	return s.survival, nil
}

// fixedSales は決まった値を返す興行収入のモデル。
type fixedSales struct {
	sales float64
}

func (m fixedSales) PredictSales(chapter15.Movie) (float64, error) { return m.sales, nil }

// fixedSurvival は決まった値を返す生存予測のモデル。
type fixedSurvival struct {
	survived bool
}

func (m fixedSurvival) Survives(chapter15.Passenger) (bool, error) { return m.survived, nil }

// call は API を呼んで、応答の状態コードと本文を返す。
func call(t *testing.T, handler http.Handler, method, path, body string) (int, string) {
	t.Helper()

	request := httptest.NewRequest(method, path, strings.NewReader(body))
	recorder := httptest.NewRecorder()
	handler.ServeHTTP(recorder, request)

	return recorder.Code, recorder.Body.String()
}

// handlerWith はスタブの置き場を差し込んだ API を作る。
func handlerWith(store chapter15.ModelStore) http.Handler {
	return chapter15.NewHandler(chapter15.NewPredictionService(store))
}

// bothModels は両方のモデルがあるスタブ。
func bothModels() stubStore {
	return stubStore{sales: fixedSales{sales: 4321.5}, survival: fixedSurvival{survived: true}}
}

func TestPredictionAPI(t *testing.T) {
	t.Parallel()

	t.Run("興行収入を予測して JSON で返す", func(t *testing.T) {
		t.Parallel()

		status, body := call(t, handlerWith(bothModels()), http.MethodPost, "/cinema/sales",
			`{"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}`)

		if status != http.StatusOK {
			t.Fatalf("状態コード = %d, want %d（本文 %s）", status, http.StatusOK, body)
		}

		var response struct {
			Sales float64 `json:"sales"`
		}
		if err := json.Unmarshal([]byte(body), &response); err != nil {
			t.Fatalf("応答が JSON ではありません: %v", err)
		}

		if response.Sales != 4321.5 {
			t.Errorf("sales = %v, want %v", response.Sales, 4321.5)
		}
	})

	t.Run("生存を予測して JSON で返す", func(t *testing.T) {
		t.Parallel()

		status, body := call(t, handlerWith(bothModels()), http.MethodPost, "/survived",
			`{"pclass": 1, "sex": "female", "age": 30, "sib_sp": 0, "parch": 0, "fare": 80, "embarked": "S"}`)

		if status != http.StatusOK {
			t.Fatalf("状態コード = %d, want %d（本文 %s）", status, http.StatusOK, body)
		}

		if !strings.Contains(body, `"survived":true`) {
			t.Errorf("本文 = %s, want survived が true", body)
		}
	})

	t.Run("年齢と乗船港は省略できる", func(t *testing.T) {
		t.Parallel()

		status, body := call(t, handlerWith(bothModels()), http.MethodPost, "/survived",
			`{"pclass": 3, "sex": "male", "sib_sp": 0, "parch": 0, "fare": 7.25}`)

		if status != http.StatusOK {
			t.Errorf("状態コード = %d, want %d（本文 %s）", status, http.StatusOK, body)
		}
	})

	t.Run("入力が不正なら 422 と理由の一覧を返す", func(t *testing.T) {
		t.Parallel()

		tests := []struct {
			name string
			path string
			body string
			want []string
		}{
			{
				name: "映画の必須の値が無い",
				path: "/cinema/sales",
				body: `{"sns1": 100}`,
				want: []string{"sns2 は必須です", "actor は必須です", "original は必須です"},
			},
			{
				name: "映画の値が負で、原作の有無が選択肢にない",
				path: "/cinema/sales",
				body: `{"sns1": -1, "sns2": 2000, "actor": 300, "original": 2}`,
				want: []string{"sns1 は 0 以上にしてください", "original は 0、1 のどれかにしてください"},
			},
			{
				name: "乗客の等級と性別が選択肢にない",
				path: "/survived",
				body: `{"pclass": 4, "sex": "unknown", "sib_sp": 0, "parch": 0, "fare": 10}`,
				want: []string{"pclass は 1、2、3 のどれかにしてください", "sex は female、male のどれかにしてください"},
			},
			{
				name: "乗船港が選択肢にない",
				path: "/survived",
				body: `{"pclass": 1, "sex": "male", "sib_sp": 0, "parch": 0, "fare": 10, "embarked": "X"}`,
				want: []string{"embarked は C、Q、S のどれかにしてください"},
			},
		}

		for _, test := range tests {
			t.Run(test.name, func(t *testing.T) {
				t.Parallel()

				status, body := call(t, handlerWith(bothModels()), http.MethodPost, test.path, test.body)

				if status != http.StatusUnprocessableEntity {
					t.Fatalf("状態コード = %d, want %d（本文 %s）", status, http.StatusUnprocessableEntity, body)
				}

				for _, want := range test.want {
					if !strings.Contains(body, want) {
						t.Errorf("本文 = %s, want %q を含む", body, want)
					}
				}
			})
		}
	})

	t.Run("JSON として読めなければ 422 を返し、内部の型名を漏らさない", func(t *testing.T) {
		t.Parallel()

		for _, body := range []string{`{"sns1": "たくさん"}`, `{ここは JSON ではない`, ``} {
			status, response := call(t, handlerWith(bothModels()), http.MethodPost, "/cinema/sales", body)

			if status != http.StatusUnprocessableEntity {
				t.Errorf("%q の状態コード = %d, want %d", body, status, http.StatusUnprocessableEntity)
			}

			if !strings.Contains(response, "JSON の形式または値の型が正しくありません") {
				t.Errorf("本文 = %s, want 形式の理由", response)
			}

			if strings.Contains(response, "chapter15") || strings.Contains(response, "float64") {
				t.Errorf("本文 = %s, want 内部の型名を含まない", response)
			}
		}
	})

	t.Run("モデルが無ければ 503 を返す", func(t *testing.T) {
		t.Parallel()

		status, body := call(t, handlerWith(stubStore{}), http.MethodPost, "/cinema/sales",
			`{"sns1": 100, "sns2": 2000, "actor": 300, "original": 1}`)

		if status != http.StatusServiceUnavailable {
			t.Fatalf("状態コード = %d, want %d（本文 %s）", status, http.StatusServiceUnavailable, body)
		}

		if !strings.Contains(body, "モデルがありません: cinema") {
			t.Errorf("本文 = %s, want モデルの名前を含む", body)
		}
	})

	t.Run("ヘルスチェックはモデルごとの状態を返す", func(t *testing.T) {
		t.Parallel()

		tests := []struct {
			name   string
			store  stubStore
			status string
		}{
			{name: "両方ある", store: bothModels(), status: "ok"},
			{name: "片方だけある", store: stubStore{sales: fixedSales{}}, status: "degraded"},
			{name: "どちらも無い", store: stubStore{}, status: "degraded"},
		}

		for _, test := range tests {
			t.Run(test.name, func(t *testing.T) {
				t.Parallel()

				status, body := call(t, handlerWith(test.store), http.MethodGet, "/health", "")

				if status != http.StatusOK {
					t.Fatalf("状態コード = %d, want %d", status, http.StatusOK)
				}

				var response struct {
					Status string          `json:"status"`
					Models map[string]bool `json:"models"`
				}
				if err := json.Unmarshal([]byte(body), &response); err != nil {
					t.Fatalf("応答が JSON ではありません: %v", err)
				}

				if response.Status != test.status {
					t.Errorf("status = %q, want %q", response.Status, test.status)
				}

				if len(response.Models) != 2 {
					t.Errorf("models = %v, want 2 件", response.Models)
				}
			})
		}
	})

	t.Run("知らないパスは 404、許していないメソッドは 405 を返す", func(t *testing.T) {
		t.Parallel()

		handler := handlerWith(bothModels())

		if status, _ := call(t, handler, http.MethodGet, "/unknown", ""); status != http.StatusNotFound {
			t.Errorf("状態コード = %d, want %d", status, http.StatusNotFound)
		}

		if status, _ := call(t, handler, http.MethodGet, "/cinema/sales", ""); status != http.StatusMethodNotAllowed {
			t.Errorf("状態コード = %d, want %d", status, http.StatusMethodNotAllowed)
		}
	})
}
