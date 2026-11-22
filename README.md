# ASM Quest

A fully local Android puzzle game where you program a tiny assembly
virtual machine to beat 45 campaign levels — plus a free-play sandbox,
a level editor with shareable codes, and a date-seeded daily challenge.

## Build

1. Open this folder in Android Studio (Hedgehog or newer, JDK 17).
2. If prompted, let Studio create or repair the Gradle wrapper.
3. Run on any device or emulator with Android 7.0 (API 24) or later.

## The language

Registers A-D, 64 memory slots, a 16x16 pixel canvas, a tone generator.

    MOV  ADD  SUB  MUL  DIV  MOD  INC  DEC  NEG
    AND  OR   XOR  NOT  SHL  SHR
    LOAD STORE PUSH POP
    CMP  JMP  JE   JNE  JG   JGE  JL   JLE  CALL RET
    IN   OUT  OUTC BEEP
    CLR  PEN  PLOT LINE RECT FILL PIX
    NOP  HLT

## Features

- 45 campaign levels: registers, loops, memory, subroutines,
  input-driven puzzles, pixel-art graphics, melodies
- Step debugger, animated playback (3 speeds), canvas time-travel
- Undo/redo, syntax highlighting, snippet chips, searchable help
- Sandbox with demo programs
- Level editor with tap-to-paint pixel grid and solvability tester
- Share levels as text codes via the clipboard (ASMQ1...)
- Daily challenge + streak, 13 achievements, star ratings, stats
- JSON backup/restore through the system file picker

## Privacy

The app declares no INTERNET permission and cannot make network
requests. All progress stays on the device; backups are files you
export yourself.
