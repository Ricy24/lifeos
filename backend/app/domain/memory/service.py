from dataclasses import dataclass
from typing import Optional

@dataclass
class MemoryCandidate:
    content: str
    memory_type: str
    confidence: float
    
class MemoryService:
    @staticmethod
    def store_memory(user_id: str, candidate: MemoryCandidate):
        # In production, writes to pgvector.
        pass
        
    @staticmethod
    def retrieve_context(user_id: str, query: str, top_k: int = 5):
        # In production, runs vector similarity.
        return ["Mocked memory context"]
