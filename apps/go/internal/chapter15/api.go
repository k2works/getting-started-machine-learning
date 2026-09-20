package chapter15

import (
	"encoding/json"
	"errors"
	"io"
	"log"
	"net/http"
)

// invalidJSON は JSON として読めない・型が合わない入力に返す理由。
// 標準の json のエラーには内部の型名が含まれるので、応答には出さない。
const invalidJSON = "JSON の形式または値の型が正しくありません"

// salesResponse は興行収入の予測の応答。
type salesResponse struct {
	Sales float64 `json:"sales"`
}

// survivalResponse は生存の予測の応答。
type survivalResponse struct {
	Survived bool `json:"survived"`
}

// healthResponse はヘルスチェックの応答。
type healthResponse struct {
	Status string          `json:"status"`
	Models map[string]bool `json:"models"`
}

// errorResponse は 1 つの理由を返すエラーの応答。
type errorResponse struct {
	Detail string `json:"detail"`
}

// validationErrorResponse は検証の理由の一覧を返すエラーの応答。
type validationErrorResponse struct {
	Detail []string `json:"detail"`
}

// NewHandler はサービスを使う API を作る。listen はしない。
func NewHandler(service *PredictionService) http.Handler {
	mux := http.NewServeMux()
	// Go 1.22 以降のルーティングは、メソッドとパスをまとめて書ける。
	// 登録していないメソッドには 405 が、登録していないパスには 404 が自動で返る。
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		health(w, service)
	})
	mux.HandleFunc("POST /cinema/sales", func(w http.ResponseWriter, r *http.Request) {
		predictSales(w, r, service)
	})
	mux.HandleFunc("POST /survived", func(w http.ResponseWriter, r *http.Request) {
		predictSurvival(w, r, service)
	})

	return mux
}

// health はモデルごとに読み込めるかどうかを返す。
func health(w http.ResponseWriter, service *PredictionService) {
	models := make(map[string]bool, 2)
	status := "ok"

	for _, model := range service.Health() {
		models[model.Name] = model.Ready

		if !model.Ready {
			status = "degraded"
		}
	}

	writeJSON(w, http.StatusOK, healthResponse{Status: status, Models: models})
}

// predictSales は映画の興行収入を予測する。
func predictSales(w http.ResponseWriter, r *http.Request, service *PredictionService) {
	var request MovieRequest
	if !decode(w, r, &request) {
		return
	}

	validated, err := request.Validate()
	if err != nil {
		writeFailure(w, err)

		return
	}

	if !validated.Valid() {
		writeJSON(w, http.StatusUnprocessableEntity, validationErrorResponse{Detail: validated.Errors})

		return
	}

	sales, err := service.PredictSales(validated.Value)
	if err != nil {
		writeFailure(w, err)

		return
	}

	writeJSON(w, http.StatusOK, salesResponse{Sales: sales})
}

// predictSurvival は乗客が生存するかを予測する。
func predictSurvival(w http.ResponseWriter, r *http.Request, service *PredictionService) {
	var request PassengerRequest
	if !decode(w, r, &request) {
		return
	}

	validated, err := request.Validate()
	if err != nil {
		writeFailure(w, err)

		return
	}

	if !validated.Valid() {
		writeJSON(w, http.StatusUnprocessableEntity, validationErrorResponse{Detail: validated.Errors})

		return
	}

	survived, err := service.PredictSurvival(validated.Value)
	if err != nil {
		writeFailure(w, err)

		return
	}

	writeJSON(w, http.StatusOK, survivalResponse{Survived: survived})
}

// decode は本文を JSON として読む。読めなければ 422 を返して false を返す。
func decode(w http.ResponseWriter, r *http.Request, request any) bool {
	decoder := json.NewDecoder(r.Body)
	if err := decoder.Decode(request); err != nil {
		writeJSON(w, http.StatusUnprocessableEntity, validationErrorResponse{Detail: []string{invalidJSON}})

		return false
	}

	// 本文に 2 つ目の JSON が続いていないかを確かめる。
	if err := decoder.Decode(new(json.RawMessage)); !errors.Is(err, io.EOF) {
		writeJSON(w, http.StatusUnprocessableEntity, validationErrorResponse{Detail: []string{invalidJSON}})

		return false
	}

	return true
}

// writeFailure はドメインのエラーを HTTP のステータスコードに変える。
func writeFailure(w http.ResponseWriter, err error) {
	if errors.Is(err, ErrModelNotFound) {
		writeJSON(w, http.StatusServiceUnavailable, errorResponse{Detail: err.Error()})

		return
	}

	writeJSON(w, http.StatusInternalServerError, errorResponse{Detail: "予測できませんでした"})
}

// writeJSON は応答を JSON で書く。
func writeJSON(w http.ResponseWriter, status int, body any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)

	if err := json.NewEncoder(w).Encode(body); err != nil {
		// ここまで来ると状態コードは送信済みなので、記録するしかない。
		log.Printf("応答を書けません: %v", err)
	}
}
