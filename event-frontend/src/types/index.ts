export interface EventDTO {
  name: string;
  description: string;
  location: string;
  city: string;
}

export interface Event extends EventDTO {
  id: string;
}

export const Priority = {
  VERY_LOW: "VERY_LOW",
  LOW: "LOW",
  MEDIUM: "MEDIUM",
  HIGH: "HIGH",
  VERY_HIGH: "VERY_HIGH"
} as const;

export type Priority = (typeof Priority)[keyof typeof Priority];

export const AreaType = {
  ENTRANCE: "ENTRANCE",
  EXIT: "EXIT",
  TRANSIT: "TRANSIT",
  PEAK_ATTRACTION: "PEAK_ATTRACTION",
  SUSTAINED_ATTRACTION: "SUSTAINED_ATTRACTION",
  GENERIC: "GENERIC"
} as const;

export type AreaType = (typeof AreaType)[keyof typeof AreaType];

export interface AreaDTO {
  name: string;
  capacity: number;
  priority: Priority;
  state?: State;
  type: AreaType;
  boundary: {
    type: "Polygon";
    coordinates: number[][][]; // GeoJSON polygon coordinates
  };
}

export interface NodeDTO {
  id?: string;
  name: string;
  type: string;
  brokerUrl: string;
  latitude: number;
  longitude: number;
}

export const UserType = {
  USER: "USER",
  ORGANIZER: "ORGANIZER",
  OPERATOR: "OPERATOR"
} as const;

export type UserType = (typeof UserType)[keyof typeof UserType];

export const State = {
  NONE: "NONE",
  LOW: "LOW",
  MEDIUM: "MEDIUM",
  HIGH: "HIGH",
  CRITICAL: "CRITICAL"
} as const;

export type State = (typeof State)[keyof typeof State];

export interface NotifyDTO {
  message: string;
  alert: any; // We can type this better later if needed
  priority: Priority;
}

export interface AnalysisStats {
  id: number;
  areaId: string;
  ts: string;
  windowSeconds: number;
  estimatedPeople: number;
  trend: string;
  servedBy: string;
  density: number;
  node: string;
}

export interface PredictionPointDTO {
  ts: string;
  estimatedPeople: number;
}

export interface ManualAlertDTO {
  ts: string;
  cause: string;
  lon: number;
  lat: number;
}

export interface MigrationDTO {
  id: number;
  areaId: string;
  podName: string;
  fromNode: string;
  toNode: string;
  previousCost?: number;
  newCost?: number;
  reason: string;
  success: boolean;
  errorMessage?: string;
  timestamp: string;
}