package setup_test

import "testing"

func TestTestingFrameworkWorks(t *testing.T) {
	t.Parallel()

	if got, want := 1+1, 2; got != want {
		t.Errorf("1 + 1 = %d, want %d", got, want)
	}
}
