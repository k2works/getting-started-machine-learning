namespace MachineLearning.Tests.Chapter09;

using System.Text;
using MachineLearning.Chapter09;

public class BikeWeatherTests : IDisposable
{
    private readonly string file = Path.GetTempFileName();

    [Fact(DisplayName = "Shift_JIS の CSV を読み込む")]
    public void ReadsShiftJis()
    {
        File.WriteAllText(this.file, "weather_id,weather\n1,晴れ\n2,曇り\n", BikeWeather.ShiftJis());

        var weather = BikeWeather.LoadWeather(this.file);

        Assert.Equal(["weather_id", "weather"], weather.Columns);
        Assert.Equal(["晴れ", "曇り"], weather.Rows.Select(row => row.Text("weather")));
    }

    [Fact(DisplayName = "学習用テスト: Shift_JIS のファイルを UTF-8 として読むと文字化けする")]
    public void MojibakeWhenUtf8()
    {
        File.WriteAllText(this.file, "晴れ", BikeWeather.ShiftJis());

        Assert.NotEqual("晴れ", File.ReadAllText(this.file, Encoding.UTF8));
    }

    [Fact(DisplayName = "タブ区切りのファイルを読み込む")]
    public void ReadsTsv()
    {
        File.WriteAllText(this.file, "weather_id\tcnt\n1\t985\n", Encoding.UTF8);

        var bike = BikeWeather.LoadBike(this.file);

        Assert.Equal(["weather_id", "cnt"], bike.Columns);
        Assert.Equal(985.0, bike.Rows[0].Number("cnt"));
    }

    [Fact(DisplayName = "天気の番号で天気の名前を結合し、名前が無い行は除く")]
    public void JoinsOnWeatherId()
    {
        var bike = Samples.Table(["weather_id", "cnt"], ["1", "100"], ["2", "50"], ["9", "1"]);
        var weather = Samples.Table(["weather_id", "weather"], ["1", "晴れ"], ["2", "曇り"]);

        var joined = BikeWeather.JoinWeather(bike, weather);

        Assert.Equal(["weather_id", "cnt", "weather"], joined.Columns);
        Assert.Equal(["晴れ", "曇り"], joined.Rows.Select(row => row.Text("weather")));
    }

    [Fact(DisplayName = "天気ごとの平均利用者数を多い順に並べる")]
    public void MeansSortedDescending()
    {
        var joined = Samples.Table(
            ["weather", "cnt"], ["晴れ", "100"], ["曇り", "50"], ["晴れ", "300"]);

        Assert.Equal(
            [KeyValuePair.Create("晴れ", 200.0), KeyValuePair.Create("曇り", 50.0)],
            BikeWeather.MeanCountByWeather(joined));
    }

    public void Dispose()
    {
        File.Delete(this.file);
        GC.SuppressFinalize(this);
    }
}
