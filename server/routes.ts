import type { Express } from "express";
import { createServer, type Server } from "http";
import { storage } from "./storage";

export async function registerRoutes(app: Express): Promise<Server> {
  app.get("/api/health", (req, res) => res.json({ status: "ok" }));
  const httpServer = createServer(app);
  return httpServer;
}