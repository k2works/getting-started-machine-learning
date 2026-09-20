package chapter15

// PredictionService は置き場からモデルを読み込んで予測する。HTTP には依存しない。
type PredictionService struct {
	store ModelStore
}

// NewPredictionService は置き場を差し込んだサービスを作る。
func NewPredictionService(store ModelStore) *PredictionService {
	return &PredictionService{store: store}
}

// PredictSales は映画の興行収入を予測する。
func (s *PredictionService) PredictSales(movie Movie) (float64, error) {
	model, err := s.store.LoadSalesModel()
	if err != nil {
		return 0, err
	}

	return model.PredictSales(movie)
}

// PredictSurvival は乗客が生存するかを予測する。
func (s *PredictionService) PredictSurvival(passenger Passenger) (bool, error) {
	model, err := s.store.LoadSurvivalModel()
	if err != nil {
		return false, err
	}

	return model.Survives(passenger)
}

// ModelHealth はモデルの名前と、読み込めるかどうか。map ではなくスライスにして並びを固定する。
type ModelHealth struct {
	Name  string
	Ready bool
}

// Health はモデルごとに読み込めるかどうかを返す。
func (s *PredictionService) Health() []ModelHealth {
	_, salesErr := s.store.LoadSalesModel()
	_, survivalErr := s.store.LoadSurvivalModel()

	return []ModelHealth{
		{Name: SalesModelName, Ready: salesErr == nil},
		{Name: SurvivalModelName, Ready: survivalErr == nil},
	}
}
