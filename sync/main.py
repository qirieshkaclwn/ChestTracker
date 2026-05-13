from fastapi import FastAPI, Depends, HTTPException, Header, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from typing import List, Optional
import datetime
import json
from sqlalchemy import Column, Integer, String, DateTime, create_engine, func
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker, Session
import uuid
import os
from dotenv import load_dotenv

# Load .env file
load_dotenv()

# --- Configuration ---
DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://user:password@localhost/chesttracker")
API_TOKEN = os.getenv("API_TOKEN", "your-secret-token")

engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

app = FastAPI(title="ChestTracker Sync API")

@app.exception_handler(RequestValidationError)
async def validation_exception_handler(request: Request, exc: RequestValidationError):
    print(f"--- VALIDATION ERROR ---")
    print(f"Errors: {exc.errors()}")
    print(f"Body: {await request.body()}")
    print(f"------------------------")
    return JSONResponse(
        status_code=422,
        content={"detail": exc.errors()},
    )

# --- Database Models ---
class ChestRecord(Base):
    __tablename__ = "chest_memories"

    id = Column(Integer, primary_key=True, index=True)
    server_id = Column(String(255), index=True, nullable=False)
    world_id = Column(String(255), index=True, nullable=False)
    key_id = Column(String(255), nullable=False, server_default="minecraft:chest")
    pos_x = Column(Integer, nullable=False)
    pos_y = Column(Integer, nullable=False)
    pos_z = Column(Integer, nullable=False)
    items_data = Column(JSONB, nullable=False)
    last_updated = Column(DateTime, server_default=func.now(), onupdate=func.now())
    updated_by = Column(UUID(as_uuid=True), nullable=False)

# --- Pydantic Models ---
class ChestUpdate(BaseModel):
    server_id: str
    world_id: str
    key_id: str
    pos_x: int
    pos_y: int
    pos_z: int
    items_data: Optional[dict] = None
    deltas: Optional[dict] = None
    updated_by: uuid.UUID

# --- Dependencies ---
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

def verify_token(x_token: str = Header(...)):
    if x_token != API_TOKEN:
        raise HTTPException(status_code=403, detail="Invalid API Token")

# --- Endpoints ---
@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/sync/update", dependencies=[Depends(verify_token)])
def update_chest(update: ChestUpdate, db: Session = Depends(get_db)):
    db_chest = db.query(ChestRecord).filter(
        ChestRecord.server_id == update.server_id,
        ChestRecord.world_id == update.world_id,
        ChestRecord.pos_x == update.pos_x,
        ChestRecord.pos_y == update.pos_y,
        ChestRecord.pos_z == update.pos_z
    ).first()

    if db_chest:
        # Smart synchronization: only update if something actually changed
        if update.items_data and db_chest.items_data == update.items_data and db_chest.key_id == update.key_id:
            return {"status": "success", "detail": "no changes detected"}
            
        if update.deltas:
            # Apply deltas to existing items_data
            new_items_data = dict(db_chest.items_data)
            # deltas is a dict of {slot_index: item_data}
            # the mod stores items as a list in items_data['items']
            if 'items' in new_items_data:
                items_list = list(new_items_data['items'])
                for slot_str, item_val in update.deltas.items():
                    try:
                        slot_idx = int(slot_str)
                        if 0 <= slot_idx < len(items_list):
                            items_list[slot_idx] = item_val
                    except ValueError:
                        continue
                new_items_data['items'] = items_list
            db_chest.items_data = new_items_data
        elif update.items_data:
            db_chest.items_data = update.items_data
            
        db_chest.key_id = update.key_id
        db_chest.last_updated = datetime.datetime.utcnow()
        db_chest.updated_by = update.updated_by
    else:
        if not update.items_data:
             raise HTTPException(status_code=400, detail="items_data is required for new records")
        db_chest = ChestRecord(
            server_id=update.server_id,
            world_id=update.world_id,
            key_id=update.key_id,
            pos_x=update.pos_x,
            pos_y=update.pos_y,
            pos_z=update.pos_z,
            items_data=update.items_data,
            updated_by=update.updated_by
        )
        db.add(db_chest)
    
    db.commit()
    return {"status": "success"}

@app.get("/sync/fetch/{server_id:path}", dependencies=[Depends(verify_token)])
def fetch_server_chests(server_id: str, db: Session = Depends(get_db)):
    chests = db.query(ChestRecord).filter(ChestRecord.server_id == server_id).all()
    return chests

@app.delete("/sync/delete/{server_id:path}/{world_id}/{pos_x}/{pos_y}/{pos_z}", dependencies=[Depends(verify_token)])
def delete_chest(server_id: str, world_id: str, pos_x: int, pos_y: int, pos_z: int, db: Session = Depends(get_db)):
    db_chest = db.query(ChestRecord).filter(
        ChestRecord.server_id == server_id,
        ChestRecord.world_id == world_id,
        ChestRecord.pos_x == pos_x,
        ChestRecord.pos_y == pos_y,
        ChestRecord.pos_z == pos_z
    ).first()

    if db_chest:
        db.delete(db_chest)
        db.commit()
        return {"status": "success"}
    else:
        raise HTTPException(status_code=404, detail="Chest not found")

@app.delete("/sync/clear/{server_id:path}", dependencies=[Depends(verify_token)])
def clear_server_chests(server_id: str, db: Session = Depends(get_db)):
    db.query(ChestRecord).filter(ChestRecord.server_id == server_id).delete()
    db.commit()
    return {"status": "success"}
