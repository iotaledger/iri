package CLI

import (
	"reflect"
	"testing"
)

func TestGetRank(t *testing.T) {

	var cli = new(CLI)

	info := []string{"A", "B", "1"}
	cli.addAttestationInfo(info)
	info = []string{"B", "C", "1"}
	cli.addAttestationInfo(info)
	info = []string{"C", "D", "1"}
	cli.addAttestationInfo(info)
	info = []string{"D", "A", "1"}
	cli.addAttestationInfo(info)
	info = []string{"A", "C", "1"}
	cli.addAttestationInfo(info)

	actural := cli.getRank("1", 1)

	if reflect.DeepEqual(len(actural), 1) != true {
		t.Error("expected", 1, "but got ", len(actural))
	}

	acturalStr := actural[0].Attestee
	expected := "C"

	if reflect.DeepEqual(acturalStr, "C") != true {
		t.Error("expected", expected, "but got ", acturalStr)
	}
}
