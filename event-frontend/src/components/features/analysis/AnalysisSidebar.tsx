import React, { useState } from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import type { AreaDTO, AnalysisStats } from '../../../types';
import { analysisApi } from '../../../api/analysisApi';
import { Card, CardHeader, CardBody } from '../../ui/Card';

interface AnalysisSidebarProps {
  areas: AreaDTO[];
}

interface ChartData {
  time: string;
  timestamp: number;
  people?: number;
  density?: number;
  predPeople?: number;
  predDensity?: number;
}

const AnalysisSidebar: React.FC<AnalysisSidebarProps> = ({ areas }) => {
  const [selectedAreaId, setSelectedAreaId] = useState<string | null>(null);
  const [analysisData, setAnalysisData] = useState<AnalysisStats[]>([]);
  const [chartData, setChartData] = useState<ChartData[]>([]);
  const [loading, setLoading] = useState<boolean>(false);

  const fetchAnalysis = async (areaId: string) => {
    setSelectedAreaId(areaId);
    setLoading(true);
    try {
      const [history, predictions] = await Promise.all([
        analysisApi.getAnalysisAll(areaId),
        analysisApi.getPredictionTrend(areaId).catch(() => []) // fallback
      ]);
      
      // Ordina per timestamp crescente se non lo sono già
      history.sort((a, b) => new Date(a.ts).getTime() - new Date(b.ts).getTime());
      
      setAnalysisData(history);
      
      // Calcola l'area in m^2 (density = people / area => area = people / density)
      let areaSize = 1;
      const validStat = history.find(s => s.density > 0 && s.estimatedPeople > 0);
      if (validStat) {
        areaSize = validStat.estimatedPeople / validStat.density;
      }

      const dataMap = new Map<number, ChartData>();
      
      // Inserisci dati storici
      history.forEach(stat => {
        const ts = new Date(stat.ts).getTime();
        const timeStr = new Date(stat.ts).toLocaleTimeString();
        const point: ChartData = {
          time: timeStr,
          timestamp: ts,
          people: stat.estimatedPeople,
          density: stat.density
        };
        dataMap.set(ts, point);
      });
      
      const lastHistoricalPoint = dataMap.size > 0 ? Array.from(dataMap.values()).pop() : null;
      
      // Unisci le predizioni, agganciando l'inizio della predizione alla fine dello storico
      if (lastHistoricalPoint && predictions.length > 0) {
         lastHistoricalPoint.predPeople = lastHistoricalPoint.people;
         lastHistoricalPoint.predDensity = lastHistoricalPoint.density;
      }
      
      predictions.forEach(pred => {
        const ts = new Date(pred.ts).getTime();
        const timeStr = new Date(pred.ts).toLocaleTimeString();
        const predDensity = pred.estimatedPeople / areaSize;
        
        if (dataMap.has(ts)) {
           const existing = dataMap.get(ts)!;
           existing.predPeople = pred.estimatedPeople;
           existing.predDensity = predDensity;
        } else {
           dataMap.set(ts, {
             time: timeStr,
             timestamp: ts,
             predPeople: pred.estimatedPeople,
             predDensity: predDensity
           });
        }
      });

      const sortedData = Array.from(dataMap.values()).sort((a, b) => a.timestamp - b.timestamp);
      setChartData(sortedData);

    } catch (e) {
      console.error(`Errore caricamento analisi per ${areaId}`, e);
      setAnalysisData([]);
      setChartData([]);
    } finally {
      setLoading(false);
    }
  };

  const latestInfo = analysisData.length > 0 ? analysisData[analysisData.length - 1] : null;

  return (
    <Card className="h-100">
      <CardHeader className="bg-info text-white">
        <h5 className="mb-0">Analisi Aree</h5>
      </CardHeader>
      <CardBody className="d-flex flex-column p-0" style={{ height: '700px' }}>
        
        <div className="p-3 border-bottom">
          <label className="form-label fw-bold">Seleziona Area:</label>
          <div className="list-group" style={{ maxHeight: '150px', overflowY: 'auto' }}>
            {areas.length === 0 && <p className="text-muted mb-0">Nessuna area definita.</p>}
            {areas.map(area => (
              <button 
                key={area.name} 
                className={`list-group-item list-group-item-action py-2 ${selectedAreaId === area.name ? 'active' : ''}`}
                onClick={() => fetchAnalysis(area.name)}
              >
                {area.name} <span className="badge bg-secondary float-end">{area.state}</span>
              </button>
            ))}
          </div>
        </div>
        
        <div className="flex-grow-1 overflow-auto p-3 bg-light">
          {selectedAreaId ? (
            loading ? (
              <div className="text-center mt-4">
                <div className="spinner-border text-info" role="status"></div>
                <p className="mt-2 text-muted">Caricamento analisi...</p>
              </div>
            ) : chartData.length > 0 ? (
              <div>
                <h6 className="fw-bold mb-3 text-center">Trend Persone Stimate ({selectedAreaId})</h6>
                <div style={{ height: '200px', width: '100%' }} className="mb-4">
                  <ResponsiveContainer>
                    <LineChart data={chartData} margin={{ top: 5, right: 5, left: -20, bottom: 5 }}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="time" tick={{fontSize: 10}} />
                      <YAxis tick={{fontSize: 10}} />
                      <Tooltip />
                      <Legend wrapperStyle={{fontSize: 12}} />
                      <Line type="monotone" dataKey="people" stroke="#8884d8" name="Storico Persone" strokeWidth={2} dot={false} connectNulls />
                      <Line type="monotone" dataKey="predPeople" stroke="#8884d8" strokeDasharray="5 5" name="Predizione Persone" strokeWidth={2} dot={false} connectNulls />
                    </LineChart>
                  </ResponsiveContainer>
                </div>

                <h6 className="fw-bold mb-3 text-center">Trend Densità [p/m²] ({selectedAreaId})</h6>
                <div style={{ height: '200px', width: '100%' }} className="mb-4">
                  <ResponsiveContainer>
                    <LineChart data={chartData} margin={{ top: 5, right: 5, left: -20, bottom: 5 }}>
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="time" tick={{fontSize: 10}} />
                      <YAxis tick={{fontSize: 10}} />
                      <Tooltip />
                      <Legend wrapperStyle={{fontSize: 12}} />
                      <Line type="monotone" dataKey="density" stroke="#82ca9d" name="Storico Densità" strokeWidth={2} dot={false} connectNulls />
                      <Line type="monotone" dataKey="predDensity" stroke="#82ca9d" strokeDasharray="5 5" name="Predizione Densità" strokeWidth={2} dot={false} connectNulls />
                    </LineChart>
                  </ResponsiveContainer>
                </div>

                {latestInfo && (
                  <Card className="border-0">
                    <CardHeader className="bg-white border-bottom-0 pt-3 pb-0">
                      <h6 className="fw-bold mb-0">Ultimi Dati Registrati</h6>
                    </CardHeader>
                    <CardBody className="pt-2 pb-3">
                      <div className="d-flex justify-content-between mb-2">
                        <span className="badge bg-primary">Trend: {latestInfo.trend}</span>
                        <span className="text-muted small">{new Date(latestInfo.ts).toLocaleTimeString()}</span>
                      </div>
                      <div className="small">
                        <div className="mb-1"><strong>Nodo di calcolo:</strong> <span className="text-secondary">{latestInfo.node}</span></div>
                        <div className="mb-1"><strong>Servito da:</strong> <span className="text-secondary">{latestInfo.servedBy || "N/A"}</span></div>
                        <div className="mb-1"><strong>Finestra temporale:</strong> <span className="text-secondary">{latestInfo.windowSeconds} s</span></div>
                      </div>
                    </CardBody>
                  </Card>
                )}
              </div>
            ) : (
              <p className="text-muted text-center mt-5">Nessun dato storico o predittivo disponibile per l'area {selectedAreaId}.</p>
            )
          ) : (
            <div className="text-center mt-5">
              <i className="bi bi-bar-chart text-muted" style={{fontSize: '3rem'}}></i>
              <p className="text-muted mt-2">Seleziona un'area dall'elenco per visualizzare i grafici e lo storico.</p>
            </div>
          )}
        </div>
      </CardBody>
    </Card>
  );
};

export default AnalysisSidebar;
